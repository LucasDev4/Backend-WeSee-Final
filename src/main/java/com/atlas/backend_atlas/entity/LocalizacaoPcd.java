package com.atlas.backend_atlas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Última localização conhecida de um PCD (uma linha por pessoa; o id é o do próprio PCD). */
@Entity
@Table(name = "localizacao_pcd")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocalizacaoPcd {

    @Id
    @Column(name = "pcd_id")
    private Long pcdId;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(name = "precisao_metros")
    private Double precisaoMetros;

    /** Horário do SERVIDOR no momento do recebimento (o relógio do celular não é confiável). */
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;
}
