package com.binding;

import com.exception.BindingException;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import jakarta.servlet.ServletException;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import com.model.*;
import java.util.*;


public class ParameterBinder {

    /** Noms de champs essayes quand le vrai nom du parametre est inconnu (arg0, arg1...). */
    private static final String[] VIDE = new String[0];

    // 1. POINT D'ENTREE : fabriquer le tableau d'arguments de Method.invoke
    public static BindingResult bind(Method method,
                                     HttpServletRequest request,
                                     HttpServletResponse response) throws BindingException {

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

            if (isNomJavaValide(nom)
                    && (isSimpleType(type) || !isInfrastructureType(type))) {
                resultat.add(nom, valeur); // valeur simple ou objet disponible dans la JSP
            }

            System.out.println("[BINDING]   arg" + i + " -> " + type.getSimpleName()
                    + " " + nom + " = " + afficher(valeur));
        }

        return resultat;
    }

    // 2. UNE VALEUR = soit un objet de la requete, soit une valeur du formulaire
    private static Object resoudreValeur(Method method, int index, Class<?> type,
                                         String nomDeclare, String nom,
                                         HttpServletRequest request,
                                         HttpServletResponse response) throws BindingException {

        // --- Objets fournis par le framework ---
        if (type == HttpServletRequest.class)  return request;
        if (type == HttpServletResponse.class) return response;
        if (type == HttpSession.class)        return request.getSession();
        if (type == ServletContext.class)     return request.getServletContext();
        // MySession est un objet d'infrastructure construit pour cette requete.
        if (type == MySession.class) {
            MySession session = new MySession();
            session.chargerDepuis(request.getSession());
            return session;
        }

        // --- Upload : MapFile est un cas particulier, pas un POJO classique ---
        if (type == MapFile.class) {
            return binderMapFile(nom, nomDeclare, request);
        }

        // --- Types simples : on lit le formulaire ---
        if (isSimpleType(type)) {
            return convertir(lireValeur(request, nomDeclare, nom, type), type, nom);
        }

        // --- 3. Objet metier (POJO) : construction puis remplissage depuis le formulaire ---
        return binderObjet(method, type, nomDeclare, nom, request);
    }

    // 2 bis. BINDING D'UN FICHIER UPLOADE VERS MapFile
    private static MapFile binderMapFile(
            String nomObjet,
            String nomDeclare,
            HttpServletRequest request
    ) throws BindingException {
        return binderMapFile(Collections.singletonList(nomObjet), nomDeclare, request);
    }

    private static MapFile binderMapFile(
            List<String> nomsPossibles,
            String nomDeclare,
            HttpServletRequest request
    ) throws BindingException {
        try {
            Part part = null;

            // Priorite aux noms correspondant aux champs Java/HTML.
            for (String nomPossible : nomsPossibles) {
                if (nomPossible != null && !nomPossible.isEmpty()) {
                    part = request.getPart(nomPossible);
                    if (estPartFichierValide(part)) {
                        break;
                    }
                }
            }

            // Un repli sur le premier fichier n'est autorise que si le nom
            // du parametre n'a pas ete conserve a la compilation.
            if (!estPartFichierValide(part)
                    && estNomGenerique(nomDeclare)) {
                for (Part candidate : request.getParts()) {
                    if (estPartFichierValide(candidate)) {
                        part = candidate;
                        break;
                    }
                }
            }

            // Aucun fichier selectionne : autoriser le champ file optionnel.
            if (part == null || part.getSize() == 0
                    || part.getSubmittedFileName() == null
                    || part.getSubmittedFileName().trim().isEmpty()) {
                System.out.println("[BINDING]   aucun fichier choisi pour '" + nomDeclare + "'");
                return null;
            }

            String nomOriginal = part.getSubmittedFileName().replace('\\', '/');
            nomOriginal = nomOriginal.substring(nomOriginal.lastIndexOf('/') + 1);

            if (nomOriginal.trim().isEmpty()) {
                return null;
            }

            int point = nomOriginal.lastIndexOf('.');
            String nom;
            String extension;

            // Les fichiers comme ".env" sont traites comme un nom sans extension.
            if (point > 0 && point < nomOriginal.length() - 1) {
                nom = nomOriginal.substring(0, point);
                extension = nomOriginal.substring(point + 1).toLowerCase(Locale.ROOT);
            } else {
                nom = nomOriginal;
                extension = "";
            }

            // Java 8 compatible; readAllBytes() n'est pas utilise.
            byte[] bytes;
            try (InputStream input = part.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int lus;
                while ((lus = input.read(buffer)) != -1) {
                    output.write(buffer, 0, lus);
                }
                bytes = output.toByteArray();
            }

            MapFile fichier = new MapFile(nom, extension, bytes);
            System.out.println("[BINDING]   fichier MapFile construit : "
                    + nomOriginal + " (" + bytes.length + " octets)");
            return fichier;

        } catch (IOException | ServletException e) {
            throw new BindingException(
                    "Impossible de recuperer le fichier upload pour le parametre '"
                            + nomDeclare + "'. Verifie la configuration multipart du servlet.", e);
        }
    }

    private static boolean estPartFichierValide(Part part) {
        return part != null
                && part.getSize() > 0
                && part.getSubmittedFileName() != null
                && !part.getSubmittedFileName().trim().isEmpty();
    }

    // 2 bis. BINDING D'OBJET (recursif : Personne { String nom; Maison m; })
    private static Object binderObjet(Method method, Class<?> typeObjet,
                                      String nomDeclare, String nomObjet,
                                      HttpServletRequest request) throws BindingException {
        if (!estObjetMetier(typeObjet)) {
            throw new BindingException("Impossible de binder automatiquement le type objet "
                    + typeObjet.getName() + " pour " + method.getName()
                    + "(). Utilise une classe metier concrete avec un constructeur sans argument.");
        }

        // Prefixes candidats : "personne." puis "" (repli sans prefixe)
        List<String> prefixes = new ArrayList<>();
        if (nomObjet != null && !nomObjet.isEmpty() && !estNomGenerique(nomDeclare)) {
            prefixes.add(nomObjet + ".");
        }
        prefixes.add("");

        // true = l'objet racine est toujours cree, meme si aucun champ n'est envoye
        Object instance = construire(typeObjet, prefixes, request, new HashSet<>(), true);

        System.out.println("[BINDING]   objet " + typeObjet.getSimpleName() + " construit");
        return instance;
    }

        
    private static Object construire(
            Class<?> type,
            List<String> prefixes,
            HttpServletRequest request,
            Set<Class<?>> enCours,
            boolean obligatoire
    ) throws BindingException {

        // Eviter les references circulaires
        if (!enCours.add(type)) {
            return null;
        }

    try {
        final Object instance;

        // Construction de l'objet
        try {
            Constructor<?> constructeur = type.getDeclaredConstructor();

            if (!constructeur.canAccess(null)) {
                constructeur.setAccessible(true);
            }

            instance = constructeur.newInstance();

        } catch (ReflectiveOperationException | SecurityException e) {
            throw new BindingException(
                "La classe " + type.getName()
                + " doit avoir un constructeur sans argument accessible.",
                e
            );
        }

        int champsLies = 0;

        // Parcourir les champs de l'objet
        for (Field champ : tousLesChamps(type)) {

            int mod = champ.getModifiers();

            if (Modifier.isStatic(mod)
                    || Modifier.isFinal(mod)
                    || champ.isSynthetic()) {
                continue;
            }

            Class<?> typeChamp = champ.getType();
            Object valeur;

            // 1. Champ fichier : le champ HTML represente le fichier complet.
            if (typeChamp == MapFile.class) {
                List<String> nomsFichier = new ArrayList<>();
                for (String prefixe : prefixes) {
                    nomsFichier.add(prefixe + champ.getName());
                }
                valeur = binderMapFile(nomsFichier, champ.getName(), request);

                if (valeur == null) {
                    continue;
                }

            // 2. Champ simple : String, int, boolean, etc.
            } else if (isSimpleType(typeChamp)) {

                String brut = null;

                for (String prefixe : prefixes) {
                    brut = request.getParameter(
                        prefixe + champ.getName()
                    );

                    if (brut != null) {
                        break;
                    }
                }

                // Garder la valeur existante si aucun paramètre n'est envoyé
                if (brut == null) {
                    continue;
                }

                valeur = convertir(
                    brut,
                    typeChamp,
                    champ.getName()
                );

            // 3. Champ objet : Maison, Adresse, etc.
            } else if (estObjetMetier(typeChamp)) {

                List<String> sousPrefixes = new ArrayList<>();

                // Priorité 1 : notation imbriquée, par exemple m.adresse
                for (String prefixe : prefixes) {
                    String sousPrefixe =
                        prefixe + champ.getName() + ".";

                    if (!sousPrefixes.contains(sousPrefixe)) {
                        sousPrefixes.add(sousPrefixe);
                    }
                }

                // Priorité 2 : notation simple, par exemple adresse
                for (String prefixe : prefixes) {
                    if (!sousPrefixes.contains(prefixe)) {
                        sousPrefixes.add(prefixe);
                    }
                }

                valeur = construire(
                    typeChamp,
                    sousPrefixes,
                    request,
                    enCours,
                    false
                );

                // Aucun champ correspondant : laisser l'objet à null
                if (valeur == null) {
                    continue;
                }

            } else {
                // Collections, tableaux et types non pris en charge
                continue;
            }

            // Affecter la valeur avec le setter ou directement le champ
            affecter(type, instance, champ, valeur);

            champsLies++;

            System.out.println(
                "[BINDING]   objet "
                + type.getSimpleName()
                + "."
                + champ.getName()
                + " = "
                + afficher(valeur)
            );
        }

        System.out.println(
            "[BINDING]   objet "
            + type.getSimpleName()
            + " ("
            + champsLies
            + " champ(s) lie(s))"
        );

        // Un objet imbriqué sans champ lié reste null
        if (champsLies == 0 && !obligatoire) {
            return null;
        }

        return instance;

    } finally {
        // Toujours libérer le type, même en cas d'erreur
        enCours.remove(type);
    }
}


    private static void affecter(Class<?> type, Object instance, Field champ, Object valeur)
            throws BindingException {
        try {
            Method setter = trouverSetter(type, champ);
            if (setter != null) {
                if (!setter.canAccess(instance)) {
                    setter.setAccessible(true);
                }
                setter.invoke(instance, valeur);
            } else {
                if (!champ.canAccess(instance)) {
                    champ.setAccessible(true);
                }
                champ.set(instance, valeur);
            }
        } catch (ReflectiveOperationException | SecurityException e) {
            throw new BindingException("Impossible d'affecter le champ '"
                    + champ.getName() + "' de " + type.getSimpleName() + ".", e);
        }
    }

    /** Classe concrete "metier" que le framework peut instancier. */
    private static boolean estObjetMetier(Class<?> t) {
        return !(t.isPrimitive() || t.isInterface() || t.isArray() || t.isEnum()
                || Modifier.isAbstract(t.getModifiers())
                || t.getName().startsWith("java.")
                || t.getName().startsWith("jakarta.servlet."));
    }

    private static List<Field> tousLesChamps(Class<?> type) {
        List<Field> champs = new ArrayList<>();
        for (Class<?> courant = type; courant != null && courant != Object.class;
             courant = courant.getSuperclass()) {
            for (Field champ : courant.getDeclaredFields()) {
                champs.add(champ);
            }
        }
        return champs;
    }

    private static Method trouverSetter(Class<?> typeObjet, Field champ) {
        String nomSetter = "set" + Character.toUpperCase(champ.getName().charAt(0))
                + champ.getName().substring(1);
        for (Class<?> courant = typeObjet; courant != null && courant != Object.class;
             courant = courant.getSuperclass()) {
            try {
                Method setter = courant.getDeclaredMethod(nomSetter, champ.getType());
                if (!Modifier.isStatic(setter.getModifiers())) {
                    return setter;
                }
            } catch (NoSuchMethodException ignored) {
                // On continue dans la classe parente.
            }
        }
        return null;
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

    // 3. CONVERSION  String  ->  type du parametre
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

    // 4. NOM DU PARAMETRE  (le nom du champ du formulaire)
    private static String nomParametre(String nomDeclare, Class<?> type) {
        if (nomDeclare != null && !estNomGenerique(nomDeclare)) return nomDeclare;
        if (type == MapFile.class) return "fichier";
        return type.getSimpleName().toLowerCase(Locale.ROOT); // repli : nom du type
    }

    /** arg0, arg1, ... = le nom reel n'a pas ete conserve a la compilation. */
    private static boolean estNomGenerique(String nom) {
        return nom == null || nom.isEmpty()
                || nom.startsWith("arg")
                && nom.substring(3).chars().allMatch(Character::isDigit);
    }

    private static String[] aliasParType(Class<?> type) {
        if (type == String.class) return new String[]{"nom", "name"};
        if (type == boolean.class || type == Boolean.class) return new String[]{"actif"};
        return VIDE;
    }

    private static boolean isInfrastructureType(Class<?> type) {
        return type == HttpServletRequest.class
                || type == HttpServletResponse.class
                || type == HttpSession.class
                || type == ServletContext.class
                || type == MySession.class;
    }

    private static boolean isNomJavaValide(String nom) {
        if (nom == null || nom.isEmpty()) return false;
        if (!Character.isJavaIdentifierStart(nom.charAt(0))) return false;
        for (int i = 1; i < nom.length(); i++) {
            if (!Character.isJavaIdentifierPart(nom.charAt(i))) return false;
        }
        return true;
    }

    // 5. OUTILS

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
                || type == ServletContext.class
                || type == MySession.class
                || (!type.isPrimitive()
                    && !type.isArray()
                    && !type.isEnum()
                    && !type.getName().startsWith("java.")
                    && !type.getName().startsWith("jakarta.servlet.")
                    && !type.isInterface()
                    && !Modifier.isAbstract(type.getModifiers()));
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
