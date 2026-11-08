package br.com.cactus.guard.party.dto;

import jakarta.validation.groups.Default;

/** Grupo de validacao da criacao (documento obrigatorio so no cadastro; e imutavel depois). */
public interface OnCreate extends Default {
}
