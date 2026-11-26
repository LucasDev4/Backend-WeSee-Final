package com.atlas.backend_atlas.service.geo;

import java.util.Locale;

/**
 * Traduz a manobra estruturada do OSRM (type + modifier + nome da via) em uma frase em português.
 * O OSRM NÃO devolve texto de instrução: só dados. Aqui só se traduz o que veio; para manobras desconhecidas
 * o resultado é null (nada é inventado) e o app mostra apenas a distância e o nome da via, se houver.
 */
public final class StepInstructions {

    private StepInstructions() {}

    /**
     * @param type     maneuver.type do OSRM (turn, depart, arrive, roundabout...)
     * @param modifier maneuver.modifier (left, slight right, straight...) ou null
     * @param name     nome da via (pode ser vazio)
     * @param exit     número da saída em rotatórias (maneuver.exit) ou null
     * @return a instrução em pt-BR, ou null se o tipo de manobra não for reconhecido
     */
    public static String build(String type, String modifier, String name, Integer exit) {
        if (type == null) return null;
        String t = type.toLowerCase(Locale.ROOT);
        String via = name == null ? "" : name.trim();
        String mod = modifier == null ? "" : modifier.toLowerCase(Locale.ROOT);

        switch (t) {
            case "depart":
                return via.isEmpty() ? "Comece a caminhar" : "Comece " + em(via);
            case "arrive": {
                String lado = lado(mod);
                return lado == null ? "Você chegou ao destino" : "Você chegou ao destino, " + lado;
            }
            case "turn": {
                String acao = acao(mod);
                if (acao == null) return null;
                return via.isEmpty() ? cap(acao) : cap(acao) + " " + em(via);
            }
            case "end of road": {
                String acao = acao(mod);
                if (acao == null) return null;
                return "No fim da via, " + acao + (via.isEmpty() ? "" : " " + em(via));
            }
            case "new name":
            case "continue": {
                String acao = mod.isEmpty() || mod.equals("straight") ? "siga em frente" : acao(mod);
                if (acao == null) return null;
                return via.isEmpty() ? cap(acao) : cap(acao) + " " + em(via);
            }
            case "fork": {
                String lado = lado(mod);
                if (lado == null) return null;
                return "Na bifurcação, mantenha-se " + lado;
            }
            case "merge": {
                String lado = lado(mod);
                return lado == null ? "Entre na via" : "Entre na via, " + lado;
            }
            case "roundabout":
            case "rotary":
                return exit != null && exit > 0
                        ? "Na rotatória, pegue a " + exit + "ª saída" + (via.isEmpty() ? "" : " " + em(via))
                        : "Entre na rotatória";
            case "exit roundabout":
            case "exit rotary":
                return via.isEmpty() ? "Saia da rotatória" : "Saia da rotatória " + em(via);
            case "roundabout turn": {
                String acao = acao(mod);
                return acao == null ? null : cap(acao) + " na rotatória";
            }
            default:
                return null;
        }
    }

    /** "vire à direita", "siga ligeiramente à esquerda"... ou null se o modificador não for reconhecido. */
    private static String acao(String mod) {
        switch (mod) {
            case "left": return "vire à esquerda";
            case "right": return "vire à direita";
            case "sharp left": return "vire acentuadamente à esquerda";
            case "sharp right": return "vire acentuadamente à direita";
            case "slight left": return "siga ligeiramente à esquerda";
            case "slight right": return "siga ligeiramente à direita";
            case "straight": return "siga em frente";
            case "uturn": return "faça o retorno";
            default: return null;
        }
    }

    private static String lado(String mod) {
        switch (mod) {
            case "left": case "sharp left": case "slight left": return "à esquerda";
            case "right": case "sharp right": case "slight right": return "à direita";
            case "straight": return "em frente";
            default: return null;
        }
    }

    /** "na Rua X", "no Largo Y" ou "em Z" quando não dá para saber o gênero. */
    static String em(String via) {
        String v = via.toLowerCase(Locale.ROOT);
        String[] femininos = {"rua ", "avenida ", "av. ", "alameda ", "travessa ", "estrada ", "praça ", "praca ",
                "ladeira ", "rodovia ", "via ", "ponte ", "passarela ", "calçada ", "calcada "};
        String[] masculinos = {"largo ", "viaduto ", "parque ", "beco ", "túnel ", "tunel ", "caminho ", "corredor "};
        for (String f : femininos) if (v.startsWith(f)) return "na " + via;
        for (String m : masculinos) if (v.startsWith(m)) return "no " + via;
        return "em " + via;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
