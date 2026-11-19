/**
 * The application layer for tax planning: taxpayer profiles, per-year workspaces and their filing
 * drafts, scenarios, the advisory surface, the HTTP API, reports and background jobs.
 *
 * <p>This module owns persistence and orchestration. It assembles {@code EngineInputs} from stored
 * state and hands them to {@code taxengine}, which fetches nothing for itself.
 */
@org.springframework.modulith.ApplicationModule(
    type = org.springframework.modulith.ApplicationModule.Type.CLOSED,
    allowedDependencies = {"shared", "auth", "taxengine :: *", "portfolio"}
)
package com.thiru.wealthlens.taxplanning;
