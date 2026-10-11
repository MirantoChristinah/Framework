package com.binding;

import com.exception.BindingException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


public class ParameterBinder {

    /** Noms de champs essayes quand le vrai nom du parametre est inconnu (arg0, arg1...). */
    private static final String[] VIDE = new String[0];

    // =====================================================================
    // 1. POINT D'ENTREE : fabriquer le tableau d'arguments de Method.invoke
    // =====================================================================
    public static BindingResult bind(Method method,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws BindingException {

        // Chaque parametre : son TYPE (String, int, boolean...) et son NOM
        // (le nom reel n'est disponible que si la classe a ete compilee
        //  avec -parameters ; sinon on obtient arg0, arg1, arg2...)
        Parameter[] params = method.getParameters();

        Object[] arguments = new Object[params.length];
        BindingResult resultat = new BindingResult(arguments);

        System.out.println("[BINDING] " + method.getDeclaringClass().getSimpleName()
                + "." + method.getName() + " -> " + params.length + " parametre(s) a lier");

        afficherParametresRecus(request);

        for (int i = 0; i < params.length; i++) {
            Class<?> type = params[i].getType();
            String nomDeclare = params[i].getName();
            String nom = nomParametre(nomDeclare, type);

            Object valeur = resoudreValeur(method, i, type, nomDeclare, nom, request, response);

            arguments[i] = valeur;                       // <- la valeur placee DANS l'argument

            if (isSimpleType(type) && isNomJavaValide(nom)) {
                resultat.add(nom, valeur);              // <- et mise dans la requete (pour la vue JSP)
            }

            System.out.println("[BINDING]   arg" + i + " -> " + type.getSimpleName()
                    + " " + nom + " = " + afficher(valeur));
        }

        return resultat;
    }

    // =====================================================================
    // 2. UNE VALEUR = soit un objet de la requete, soit une valeur du formulaire
    // =====================================================================
    private static Object resoudreValeur(Method method, int index, Class<?> type,
                                         String nomDeclare, String nom,
                                         HttpServletRequest request,
                                         HttpServletResponse response) throws BindingException {

        // --- 4. Les objets fournis par le framework ---
        if (type == HttpServletRequest.class)  return request;
        if (type == HttpServletResponse.class) return response;
        if (type == HttpSession.class)        return request.getSession();
        if (type == ServletContext.class)     return request.getServletContext();

        // --- 1. Les types simples : on lit le formulaire ---
        if (isSimpleType(type)) {
            return convertir(lireValeur(request, nomDeclare, nom, type), type, nom);
        }

        // --- 3. Un objet metier : impossible pour le Sprint 7 ---
        throw new BindingException(
                "le parametre " + (index + 1) + " de la methode "
                        + method.getName() + "() est de type OBJET ("
                        + type.getName() + "). Le framework ne sait pas fabriquer cet objet : "
                        + "le binding d'objet n'est pas encore implemente. "
                        + "Utilise des parametres simples (String, int, boolean, ...) "
                        + "ou HttpServletRequest / HttpServletResponse.");
    }

    /** Lit la valeur brute dans la requete, avec les noms de repli si besoin. */
    private static String lireValeur(HttpServletRequest request, String nomDeclare,
                                     String nom, Class<?> type) {
        String brut = request.getParameter(nom);
        if (brut != null) return brut;

        // Nom de parametre inconnu (compile sans -parameters) : on essaie des alias
        if (nomDeclare == null || estNomGenerique(nomDeclare)) {
            for (String alias : aliasParType(type)) {
                String valeur = request.getParameter(alias);
                if (valeur != null) {
                    System.out.println("[BINDING]   (nom reel du parametre inconnu : "
                            + nomDeclare + ") -> champ '" + alias + "' utilise");
                    return valeur;
                }
            }
        }
        return null;
    }

    // =====================================================================
    // 3. CONVERSION  String  ->  type du parametre
    // =====================================================================
    private static Object convertir(String brut, Class<?> type, String nom) throws BindingException {
        boolean absent = (brut == null);

        if (absent || (brut.isEmpty() && type != String.class)) {
            return valeurParDefaut(type, nom);
        }

        try {
            if (type == String.class) {
                return brut;
            } else if (type == char.class || type == Character.class) {
                if (brut.length() != 1) throw new NumberFormatException("un seul caractere attendu, recu '" + brut + "'");
                return brut.charAt(0);

            } else if (type == boolean.class || type == Boolean.class) {
                return convertirBooleen(brut);

            } else if (type == int.class || type == Integer.class)          return Integer.parseInt(brut.trim());
            else if (type == long.class || type == Long.class)             return Long.parseLong(brut.trim());
            else if (type == short.class || type == Short.class)           return Short.parseShort(brut.trim());
            else if (type == byte.class || type == Byte.class)             return Byte.parseByte(brut.trim());
            else if (type == float.class || type == Float.class)           return Float.parseFloat(brut.trim());
            else if (type == double.class || type == Double.class)         return Double.parseDouble(brut.trim());
            else if (type == BigInteger.class)                             return new BigInteger(brut.trim());
            else if (type == BigDecimal.class)                             return new BigDecimal(brut.trim());
            else if (type.isEnum())                                         return convertirEnum(type, brut);

        } catch (NumberFormatException | ArithmeticException e) {
            throw new BindingException("la valeur \"" + brut + "\" recue pour le parametre '"
                    + nom + "' n'est pas convertible en " + type.getSimpleName() + ".", e);
        }

        throw new BindingException("type non gere par le binding : " + type.getName());
    }

    /** Les cases a cocher envoient "on" quand elles sont cochees. */
    private static Boolean convertirBooleen(String brut) {
        String v = brut.trim().toLowerCase(Locale.ROOT);
        if (v.equals("true") || v.equals("on") || v.equals("oui") || v.equals("yes") || v.equals("1")) return Boolean.TRUE;
        if (v.equals("false") || v.equals("off") || v.equals("non") || v.equals("no") || v.equals("0")) return Boolean.FALSE;
        throw new NumberFormatException("'" + brut + "' n'est pas un booleen");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object convertirEnum(Class<?> type, String brut) {
        String v = brut.trim();
        try {
            return Enum.valueOf((Class<Enum>) type.asSubclass(Enum.class), v);
        } catch (IllegalArgumentException e) {
            for (Object c : type.getEnumConstants()) {
                if (((Enum<?>) c).name().equalsIgnoreCase(v)) return c;
            }
            throw e;
        }
    }

    /** 2. "par defaut null" : null pour un objet, la valeur neutre pour un primitif. */
    private static Object valeurParDefaut(Class<?> type, String nom) throws BindingException {
        System.out.println("[BINDING]   aucune valeur pour '" + nom + "' -> valeur par defaut");
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return Boolean.FALSE;
        if (type == char.class)    return '\0';
        if (type == int.class)     return 0;
        if (type == long.class)    return 0L;
        if (type == short.class)   return (short) 0;
        if (type == byte.class)    return (byte) 0;
        if (type == float.class)   return 0f;
        if (type == double.class)  return 0d;
        throw new BindingException("type primitif non gere : " + type.getName());
    }

    // =====================================================================
    // 4. NOM DU PARAMETRE  (le nom du champ du formulaire)
    // =====================================================================
    private static String nomParametre(String nomDeclare, Class<?> type) {
        if (nomDeclare != null && !estNomGenerique(nomDeclare)) return nomDeclare;
        return type.getSimpleName().toLowerCase(Locale.ROOT); // repli : nom du type
    }

    /** arg0, arg1, ... = le nom reel n'a pas ete conserve a la compilation. */
    private static boolean estNomGenerique(String nom) {
        return nom.isEmpty() || nom.startsWith("arg") && nom.substring(3).chars().allMatch(Character::isDigit);
    }

    private static String[] aliasParType(Class<?> type) {
        if (type == String.class) return new String[]{"nom", "name"};
        if (type == boolean.class || type == Boolean.class) return new String[]{"actif"};
        return VIDE;
    }

    private static boolean isNomJavaValide(String nom) {
        if (nom == null || nom.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(nom.charAt(0))) return false;
        for (int i = 1; i < nom.length(); i++) {
            if (!Character.isJavaIdentifierPart(nom.charAt(i))) return false;
        }
        return true;
    }

    // =====================================================================
    // 5. OUTILS
    // =====================================================================

    /** Un type est "simple" si le framework sait le fabriquer tout seul. */
    public static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Character.class
                || type == Boolean.class
                || Number.class.isAssignableFrom(type)
                || type.isEnum();
    }

    /** Le framework sait-il fournir une valeur pour ce type ? (surcharges de methodes) */
    public static boolean isBindable(Class<?> type) {
        return isSimpleType(type)
                || type == HttpServletRequest.class
                || type == HttpServletResponse.class
                || type == HttpSession.class
                || type == ServletContext.class;
    }

    /** Tout ce que la requete contient : permet de "verifier les parametres". */
    public static void afficherParametresRecus(HttpServletRequest request) {
        Map<String, String[]> tous = request.getParameterMap();
        if (tous == null || tous.isEmpty()) {
            System.out.println("[BINDING]   (aucun parametre dans la requete)");
            return;
        }
        Set<String> noms = new LinkedHashSet<>(tous.keySet());
        for (String nom : noms) {
            String[] valeurs = tous.get(nom);
            String aff = (valeurs != null && valeurs.length > 0) ? valeurs[0] : "";
            System.out.println("[BINDING]   requete: " + nom + " = \"" + aff + "\"");
        }
    }

    private static String afficher(Object valeur) {
        if (valeur == null) return "null";
        if (valeur instanceof String) return "\"" + valeur + "\"";
        return String.valueOf(valeur);
    }
}