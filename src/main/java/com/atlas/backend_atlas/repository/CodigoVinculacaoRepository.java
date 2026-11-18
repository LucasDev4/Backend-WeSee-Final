package com.atlas.backend_atlas.repository;

import com.atlas.backend_atlas.entity.CodigoVinculacao;
import com.atlas.backend_atlas.entity.StatusCodigo;
import com.atlas.backend_atlas.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CodigoVinculacaoRepository extends JpaRepository<CodigoVinculacao, Long> {

    Optional<CodigoVinculacao> findByCodigoHash(String codigoHash);

    boolean existsByCodigoHash(String codigoHash);

    long countByPcdIdAndCriadoEmAfter(Long pcdId, LocalDateTime desde);

    /** Gerar um código novo cancela os anteriores ainda disponíveis do mesmo PCD. */
    @Modifying(flushAutomatically = true)
    @Query("update CodigoVinculacao c set c.status = :novo, c.revogadoEm = :agora "
            + "where c.pcd.id = :pcdId and c.status = :atual")
    int revogarDisponiveis(@Param("pcdId") Long pcdId, @Param("agora") LocalDateTime agora,
                           @Param("novo") StatusCodigo novo, @Param("atual") StatusCodigo atual);

    /**
     * Consumo ATÔMICO: um único UPDATE condicional. Se duas requisições usarem o mesmo código ao mesmo tempo,
     * só uma recebe 1 (as demais recebem 0).
     */
    @Modifying(flushAutomatically = true)
    @Query("update CodigoVinculacao c set c.status = :novo, c.utilizadoEm = :agora, c.utilizadoPor = :usuario "
            + "where c.id = :id and c.status = :atual and c.expiraEm > :agora")
    int consumir(@Param("id") Long id, @Param("novo") StatusCodigo novo, @Param("atual") StatusCodigo atual,
                 @Param("agora") LocalDateTime agora, @Param("usuario") User usuario);
}
