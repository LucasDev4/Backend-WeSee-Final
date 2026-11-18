package com.atlas.backend_atlas.repository;

import com.atlas.backend_atlas.entity.TentativaVinculacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface TentativaVinculacaoRepository extends JpaRepository<TentativaVinculacao, Long> {

    long countByUsuarioIdAndOcorridoEmAfter(Long usuarioId, LocalDateTime desde);
}
