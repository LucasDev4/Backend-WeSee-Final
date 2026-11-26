package com.atlas.backend_atlas.mapper;

import com.atlas.backend_atlas.dto.RegisterRequest;
import com.atlas.backend_atlas.dto.UserResponse;
import com.atlas.backend_atlas.entity.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-11-18T08:27:45-0300",
    comments = "version: 1.6.3, compiler: javac, environment: Java 26.0.1 (Oracle Corporation)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public User toEntity(RegisterRequest request) {
        if ( request == null ) {
            return null;
        }

        User.UserBuilder user = User.builder();

        user.nome( request.nome() );
        user.cpf( request.cpf() );
        user.email( request.email() );
        user.senha( request.senha() );
        user.telefone( request.telefone() );
        user.tipo( request.tipo() );

        return user.build();
    }

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        Long id = null;
        String nome = null;
        String cpf = null;
        String email = null;
        String telefone = null;
        String tipo = null;

        id = user.getId();
        nome = user.getNome();
        cpf = user.getCpf();
        email = user.getEmail();
        telefone = user.getTelefone();
        tipo = user.getTipo();

        UserResponse userResponse = new UserResponse( id, nome, cpf, email, telefone, tipo );

        return userResponse;
    }
}
