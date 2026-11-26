package com.atlas.backend_atlas.dto;

/** Resultado de POST /api/pcd/localizacao. [armazenada] = false quando ninguém está autorizado a ver a localização. */
public record PublicarLocalizacaoResponse(boolean armazenada, String mensagem) {}
