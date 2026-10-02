SERVLET_API_JAR="/opt/tomcat/lib/servlet-api.jar"
TARGET_LIB_DIR="/home/miranto/L3/MrNaina/FrameworkTest/lib"
JAR_NAME="framework.jar"

SOURCES="sources.txt"

echo "[1/4] Nettoyage local..."
rm -rf bin
mkdir -p bin

echo "[2/4] Compilation globale du Framework..."
# Generation de la liste de tous les fichiers .java (recursif)
find src/main/java -name "*.java" > "$SOURCES"

# Compilation de tous les fichiers listes
# -parameters : conserve le NOM des parametres des methodes (indispensable
#               pour le binding du Sprint 7 : save(String nom, int age) ...)
javac -encoding UTF-8 -parameters -d bin -cp "$SERVLET_API_JAR" @"$SOURCES"

if [ $? -ne 0 ]; then
    echo
    echo "[ERREUR] La compilation a echoue !"
    rm -f "$SOURCES"
    exit 1
fi
# Suppression du fichier temporaire si tout s'est bien passe
rm -f "$SOURCES"
echo "[OK] Compilation reussie de toutes les classes."

echo "[3/4] Creation du JAR..."
jar -cf "$JAR_NAME" -C bin .
if [ $? -ne 0 ]; then
    echo "[ERREUR] Impossible de creer le fichier JAR."
    exit 1
fi
echo "[OK] Fichier $JAR_NAME cree avec succes."

echo "[4/4] Deploiement vers le projet de test..."
# Verification si le dossier de destination existe, sinon on le cree
mkdir -p "$TARGET_LIB_DIR"

# Suppression de l'ancien JAR s'il existe
if [ -f "$TARGET_LIB_DIR/$JAR_NAME" ]; then
    echo "Suppression de l'ancien $JAR_NAME dans le dossier de test..."
    rm -f "$TARGET_LIB_DIR/$JAR_NAME"
fi

# Copie du nouveau JAR
cp "$JAR_NAME" "$TARGET_LIB_DIR/"

if [ $? -ne 0 ]; then
    echo "[ERREUR] Impossible de copier le JAR vers le dossier de test."
    exit 1
fi

echo
echo "============================================"
echo "   Termine ! Framework deployee dans :"
echo "   $TARGET_LIB_DIR/$JAR_NAME"
echo "============================================"