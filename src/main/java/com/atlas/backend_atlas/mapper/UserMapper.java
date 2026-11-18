package com.atlas.backend_atlas.mapper;

import org.mapstruct.Mapper;

import com.atlas.backend_atlas.dto.RegisterRequest;
import com.atlas.backend_atlas.dto.UserResponse;
import com.atlas.backend_atlas.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toEntity(RegisterRequest request);

    UserResponse toResponse(User user);

}