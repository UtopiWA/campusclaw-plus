package com.campusclaw.mcp;

import tools.jackson.databind.JsonNode;

public record McpToolView(Long id, String name, String description, JsonNode inputSchema) {
}
