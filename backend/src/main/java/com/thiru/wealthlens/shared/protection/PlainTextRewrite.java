package com.thiru.wealthlens.shared.protection;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns every value still stored as {@code k1:} ciphertext into plain text, in every table of the schema (W-428a, D-1036): a
 * text value that is ciphertext as a whole (a column the converters encrypted, a JSON column encrypted whole), a ciphertext
 * string inside a JSON value (the actor and request copies {@code CorporateActionListConverter} and {@code AssetRequestConverter}
 * sealed), and a file's bytes. It finds them by their form rather than by a list of columns, so a column nobody listed cannot
 * be missed. One transaction per column; a value that will not decrypt stops the run with that column untouched. A second run
 * finds nothing to change. Removed with the cipher by W-428c.
 */
@Component
@RequiredArgsConstructor
public class PlainTextRewrite {

    private static final String KEY_ID = FieldCipher.KEY_ID;
    /** A JSON string whose whole content is ciphertext: the quotes, the key id, standard Base64. */
    private static final Pattern SEALED_JSON_STRING = Pattern.compile("\"" + KEY_ID + "[A-Za-z0-9+/]+=*\"");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final FieldCipher cipher;

    /** What one column held and what was done: {@code rewritten} rows made plain, {@code left} rows naming {@code k1:} as plain text. */
    public record RewrittenColumn(String table, String column, int rewritten, int left) {

        public String describe() {
            return table + "." + column + ": " + rewritten + " made plain" + (left == 0 ? "" : ", " + left + " left (no ciphertext in them)");
        }
    }

    public List<RewrittenColumn> rewriteAll() {
        List<RewrittenColumn> done = new ArrayList<>();
        for (Map<String, Object> column : jdbc.queryForList("SELECT table_name, column_name, data_type FROM information_schema.columns"
                + " WHERE table_schema = current_schema() AND data_type IN ('text', 'character varying', 'bytea')"
                + " AND table_name <> 'flyway_schema_history' ORDER BY table_name, column_name")) {
            String table = (String) column.get("table_name");
            String name = (String) column.get("column_name");
            RewrittenColumn result = "bytea".equals(column.get("data_type"))
                    ? transactions.execute(status -> rewriteBytes(table, name))
                    : transactions.execute(status -> rewriteText(table, name));
            if (result != null && (result.rewritten() > 0 || result.left() > 0)) {
                done.add(result);
            }
        }
        return done;
    }

    private RewrittenColumn rewriteText(String table, String column) {
        int rewritten = 0;
        int left = 0;
        for (Map<String, Object> row : jdbc.queryForList("SELECT ctid::text AS row_id, " + quoted(column) + " AS stored FROM "
                + quoted(table) + " WHERE strpos(" + quoted(column) + ", '" + KEY_ID + "') > 0")) {
            String stored = (String) row.get("stored");
            String plain = stored.startsWith(KEY_ID) ? cipher.decrypt(stored) : openSealedStrings(stored);
            if (plain.equals(stored)) {
                left++;
            } else {
                jdbc.update("UPDATE " + quoted(table) + " SET " + quoted(column) + " = ? WHERE ctid = ?::tid", plain, row.get("row_id"));
                rewritten++;
            }
        }
        return new RewrittenColumn(table, column, rewritten, left);
    }

    private RewrittenColumn rewriteBytes(String table, String column) {
        int rewritten = 0;
        for (Map<String, Object> row : jdbc.queryForList("SELECT ctid::text AS row_id, " + quoted(column) + " AS stored FROM "
                + quoted(table) + " WHERE substring(" + quoted(column) + " FROM 1 FOR 3) = convert_to('" + KEY_ID + "', 'UTF8')")) {
            jdbc.update("UPDATE " + quoted(table) + " SET " + quoted(column) + " = ? WHERE ctid = ?::tid",
                    cipher.decryptBytes((byte[]) row.get("stored")), row.get("row_id"));
            rewritten++;
        }
        return new RewrittenColumn(table, column, rewritten, 0);
    }

    private String openSealedStrings(String json) {
        Matcher sealed = SEALED_JSON_STRING.matcher(json);
        StringBuilder plain = new StringBuilder();
        while (sealed.find()) {
            String token = sealed.group();
            sealed.appendReplacement(plain, Matcher.quoteReplacement(JSON.writeValueAsString(cipher.decrypt(token.substring(1, token.length() - 1)))));
        }
        return sealed.appendTail(plain).toString();
    }

    private static String quoted(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
