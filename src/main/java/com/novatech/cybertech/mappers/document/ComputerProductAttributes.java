package com.novatech.cybertech.mappers.document;

import com.novatech.cybertech.entities.enums.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import static com.novatech.cybertech.entities.enums.Category.COMPUTER;

@Data
@Builder
@AllArgsConstructor
public class ComputerProductAttributes implements SpecificProductAttributes {
    @Field(type = FieldType.Integer)
    private Integer ram;

    @Field(type = FieldType.Integer)
    private Integer memory;

    @Field(type = FieldType.Keyword)
    private String cpu;

    @Field(type = FieldType.Keyword)
    private String gpu;

    @Field(type = FieldType.Keyword)
    private String os;

    @Field(type = FieldType.Keyword)
    private String displayType;

    @Field(type = FieldType.Keyword)
    private String connectivity;

    @Override
    public Category getProductCategory() {
        return COMPUTER;
    }
}