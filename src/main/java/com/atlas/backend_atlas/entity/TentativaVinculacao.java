package com.atlas.backend_atlas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Registro de uma tentativa FALHA de usar um código (limitação contra força bruta). */
@Entity
@Table(name = "tentativa_vinculacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TentativaVinculacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "ocorrido_em", nullable = false)
    private LocalDateTime ocorridoEm;
}
