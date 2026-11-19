/**
 * The child processes that read untrusted bytes for the application ({@code DR 14} O6, "Parsing isolation"; W-131f, W-133a): a
 * PDF certificate, an uploaded salary workbook. Each runs from the application's own jar with a heap, CPU and wall-clock limit, an
 * empty environment, no name lookups and no Java sockets, and is killed whatever happens.
 */
package com.thiru.wealthlens.taxplanning.isolation;
