# Creation d'une Pull Request de Sprint0 vers main

## 1. Se placer sur la branche Sprint0

git checkout Sprint0

## 2. Envoyer la branche sur GitHub

git push -u origin Sprint0

## 3. Creer la Pull Request sur GitHub

* Ouvrir le depot GitHub.
* Aller dans l'onglet "Pull Requests".
* Cliquer sur "New Pull Request".
* Choisir :

  * Base branch : main
  * Compare branch : Sprint0
* Cliquer sur "Create Pull Request".
* Saisir le titre et la description.
* Valider avec "Create Pull Request".

## 4. Fusionner la Pull Request

* Cliquer sur "Merge Pull Request".
* Cliquer sur "Confirm Merge".

## 5. Supprimer la branche apres fusion

git checkout main

git pull origin main

git branch -d Sprint0

git push origin --delete Sprint0
