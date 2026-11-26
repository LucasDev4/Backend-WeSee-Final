package com.atlas.backend_atlas.repository;

import com.atlas.backend_atlas.entity.StatusVinculo;
import com.atlas.backend_atlas.entity.VinculoResponsavel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VinculoRepository extends JpaRepository<VinculoResponsavel, Long> {

    Optional<VinculoResponsavel> findByResponsavelIdAndPcdIdAndStatus(Long responsavelId, Long pcdId, StatusVinculo status);

    boolean existsByResponsavelIdAndPcdIdAndStatus(Long responsavelId, Long pcdId, StatusVinculo status);

    List<VinculoResponsavel> findByResponsavelIdOrderByCriadoEmDesc(Long responsavelId);

    List<VinculoResponsavel> findByPcdIdOrderByCriadoEmDesc(Long pcdId);

    List<VinculoResponsavel> findByPcdIdAndStatus(Long pcdId, StatusVinculo status);
}
