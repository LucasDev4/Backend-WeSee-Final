package com.atlas.backend_atlas.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Guarda o conjunto de permissões numa coluna VARCHAR ("LOCALIZACAO,ALERTAS"). */
@Converter
public class PermissaoSetConverter implements AttributeConverter<Set<Permissao>, String> {

    @Override
    public String convertToDatabaseColumn(Set<Permissao> attribute) {
        if (attribute == null || attribute.isEmpty()) return "";
        return EnumSet.copyOf(attribute).stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<Permissao> convertToEntityAttribute(String dbData) {
        Set<Permissao> set = EnumSet.noneOf(Permissao.class);
        if (dbData == null || dbData.isBlank()) return set;
        Arrays.stream(dbData.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(Permissao::valueOf).forEach(set::add);
        return set;
    }
}
