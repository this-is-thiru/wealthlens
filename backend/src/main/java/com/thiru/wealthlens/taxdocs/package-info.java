/**
 * Tax document ingestion: parsing the TDS certificate and its predecessors, recording parse attempts
 * with per-field confidence, and reconciling a document against a computation.
 *
 * <p>This is the module that consumes untrusted bytes from an unknown generator, so it is kept
 * separate from both the rules and the computation.
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    allowedDependencies = {"shared", "taxrules", "taxengine"}
)
package com.thiru.wealthlens.taxdocs;
