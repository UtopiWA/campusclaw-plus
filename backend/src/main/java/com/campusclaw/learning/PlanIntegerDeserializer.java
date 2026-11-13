package com.campusclaw.learning;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/** 计划预算拒绝小数和字符串强转，避免1.5天被静默截为1天。 */
public final class PlanIntegerDeserializer extends ValueDeserializer<Integer> {
    @Override
    public Integer deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw new IllegalArgumentException("Plan budget must be an integer");
        }
        return parser.getIntValue();
    }
}
