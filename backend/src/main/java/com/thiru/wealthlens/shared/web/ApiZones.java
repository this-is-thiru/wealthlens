package com.thiru.wealthlens.shared.web;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Every route's prefix, `/api/v1/{zone}/{module}` (D-853 C4a, D-855): the zone from the controller's annotation, the module from its
 * package, so a URL says who may call it and which module answers, and a new route cannot miss either. A controller in a module
 * missing from {@link #MODULE_PATHS} gets no prefix, which `RouteAccessIntegrationTest` fails on.
 */
@Configuration
public class ApiZones implements WebMvcConfigurer {

    public static final String VERSION = "/api/v1";

    private static final String BASE_PACKAGE = "com.thiru.wealthlens.";

    /** The module's name in a URL: short, and the word a caller knows (`plans/W-358c.md` (tag docs-history-m3)). */
    static final Map<String, String> MODULE_PATHS = Map.of("auth", "auth", "brokercharges", "charges", "corporate", "corporate-actions",
            "finance", "finances", "helper", "templates", "portfolio", "portfolio", "shared", "privacy", "taxplanning", "tax");

    private static final Map<Class<? extends Annotation>, String> ZONES = Map.of(PublicApi.class, "public", MeApi.class, "me",
            AdminApi.class, "admin");

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        ZONES.forEach((zone, zonePath) -> MODULE_PATHS.forEach((module, modulePath) -> configurer.addPathPrefix(
                VERSION + "/" + zonePath + "/" + modulePath, type -> isIn(type, zone, module))));
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new CallerEmailResolver());
    }

    private static boolean isIn(Class<?> type, Class<? extends Annotation> zone, String module) {
        Class<?> controller = ClassUtils.getUserClass(type);
        return AnnotatedElementUtils.hasAnnotation(controller, zone) && module.equals(moduleOf(controller));
    }

    static String moduleOf(Class<?> controller) {
        String name = controller.getPackageName();
        return name.startsWith(BASE_PACKAGE) ? name.substring(BASE_PACKAGE.length()).split("\\.")[0] : "";
    }
}
