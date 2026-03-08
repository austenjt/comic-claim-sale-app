package org.example.functions.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.example.functions.model.enums.ComicGrade;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class GradeSerializer extends JsonSerializer<ComicGrade> {

    @Override
    public void serialize(ComicGrade value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeNumber(BigDecimal.valueOf(value.getNumericGrade()).setScale(1, RoundingMode.HALF_UP));
        }
    }
}
