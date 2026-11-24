package org.logetd.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Masque les numéros de téléphone avant stockage et diffusion.
 * Les formats tunisiens internationalisés ({@code +216...}, {@code 216...}, {@code 00216...})
 * et les numéros locaux à 8 chiffres sont remplacés par {@code +216******}.
 */
@Service
public class PhoneMaskingService {

    public static final String MASK = "+216******";

    // Un séparateur n'est consommé que s'il est suivi d'un chiffre : l'espace qui suit
    // le numéro ne doit pas être avalé par le masque.
    private static final Pattern INTERNATIONAL = Pattern.compile(
            "(?:00|\\+)?216[\\s.\\-]?(?:\\d(?:[\\s.\\-](?=\\d))?){8,}");
    private static final Pattern LOCAL = Pattern.compile(
            "(?<![\\d+])[2459](?:\\d(?:[\\s.\\-](?=\\d))?){7}(?!\\d)");

    public String mask(String text) {
        if (text == null) {
            return null;
        }
        return LOCAL.matcher(INTERNATIONAL.matcher(text).replaceAll(MASK)).replaceAll(MASK);
    }
}
