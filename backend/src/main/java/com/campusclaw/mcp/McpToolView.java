package com.campusclaw.mcp;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolView(Long id, String name, String description, JsonNode inputSchema) {
}
