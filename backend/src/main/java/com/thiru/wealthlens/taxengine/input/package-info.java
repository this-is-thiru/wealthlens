/**
 * Everything the engine may read about a person and a year. {@code taxplanning} builds
 * {@code EngineInputs} from stored state and hands it in; this is why the package is a named
 * interface (decision D-048).
 */
@org.springframework.modulith.NamedInterface("input")
package com.thiru.wealthlens.taxengine.input;
