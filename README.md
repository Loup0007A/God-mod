# God Mod — simulation de dieux (Forge 1.21.11)

Chaque joueur commence **mortel**, gagne en puissance physique et mentale, puis devient **dieu** :
il convertit des fidèles par le PvP, soumet d'autres dieux, bâtit son monde divin, mène des guerres
et doit survivre aux rébellions de ses propres fidèles.

- Minecraft **1.21.11**, Forge **61.2.x**, Java 21
- À installer sur le serveur **et** chez les joueurs (objets et textures du mod)
- Interface : `/dieu` (ou clic droit avec le **Codex divin**, donné à la première connexion)

## Compiler le .jar avec GitHub

1. Envoyez tout le contenu de ce dossier dans le dépôt `God-mod` (racine du dépôt).
2. Le workflow `.github/workflows/build.yml` se lance à chaque push.
3. Onglet **Actions** → dernière exécution → **Artifacts** → `godmod-jar`.
4. Placez `godmod-0.1.0.jar` dans le dossier `mods/` du serveur et des joueurs.

En local : `./gradlew build` (le .jar est dans `build/libs/`).

## Progression

| Palier | Essence | Condition | Plafond de caractéristique |
|---|---|---|---|
| Mortel | 0 | — | 3 |
| Éveillé | 250 | — | 6 |
| Demi-dieu | 1 000 | peut convertir (3 fidèles max) | 10 |
| Dieu | 3 000 | 1 fidèle direct | 15 |
| Dieu suprême | 12 000 | 8 fidèles au total | 25 |

**Essence** : temps de jeu (2/min), monstres (1–3), victoires PvP (25), conversions (60),
déicide (500), tournoi gagné (300).
**Caractéristiques** (1 point / 200 Essence + 3 par palier) : Force (dégâts), Vitalité (vie),
Agilité (vitesse), Esprit (Foi max, régénération, puissance et recharge des artefacts).
**Foi** : ressource des pouvoirs, régénérée par le temps, l'Esprit et le nombre de fidèles.

## Règles divines

- **Conversion** : un demi-dieu ou un dieu qui tue un joueur moins puissant en fait son fidèle.
  Le fidèle reverse 10 % de ses gains à son dieu (dîme). Les dieux vaincus deviennent vassaux.
- **Déicide** : un non-dieu qui tue un dieu le fait chuter (redevient mortel, −50 % d'Essence).
  Le vainqueur choisit : **devenir dieu et l'asservir** (avec ses fidèles) ou refuser.
- **Rébellion** (`/rebellion`) : un fidèle se soulève contre son dieu (une fois tous les 3 jours
  par joueur), 60 s de ralliement, 10 min de combat. Réussite : le dieu chute et le meneur peut
  prendre le trône. Échec : −30 % d'Essence, −25 % d'argent, Parjure 3 jours (stats ÷ 2).
- **Dieu inactif** (7 jours) : `/trone reclamer <dieu>` lance un tournoi pour son trône.
- **Tournoi** : duels 1v1 en poules (≤ 6 joueurs : une poule ; au-delà : poules de 3–5 puis
  élimination directe). Morts annulées pendant les duels, 3 min max par duel.
- **Guerre** (`/guerre`) : 30 min. Le dieu adverse tué → son panthéon entier est soumis,
  et les mondes divins des deux camps peuvent être saccagés pendant la guerre.

## Mondes

- **Monde principal** : ressources.
- **Monde divin** (`/monde`) : dimension vide `godmod:divine`, une parcelle par joueur.
  Elle démarre avec un bloc de terre **indestructible tant qu'aucun bloc n'est posé**.
  Rayon = 16 + 8 × fidèles (total) + 8 × palier, plafonné à 400.
- **Nether et End bloqués** : leurs items sont en vente au shop (`/godadmin netherend` pour changer).

## Boutique

`/shop` : Nether, End, ressources, artefacts. Revente 5 à 10 fois moins chère que l'achat.
**Premium** (Gemmes, attribuées par un admin) : désactivé par défaut, `/pay enable` / `/pay disable`.

## Artefacts (réservés aux dieux du bon domaine)

| Artefact | Domaine | Effet | Foi |
|---|---|---|---|
| Trident des Marées | Océan | Vague qui repousse, blesse et ralentit | 30 |
| Sceptre de Foudre | Ciel | Foudre là où vous visez (plus d'éclairs avec le palier) | 40 |
| Orbe du Néant | Cosmos | Trou noir qui aspire puis implose | 60 |

## Commandes

Joueurs : `/dieu` · `/dieu profil [joueur]` · `/dieu stats <carac>` · `/dieu specialisation <ocean|ciel|cosmos>`
· `/dieu pouvoir <benir|convoquer|chatier|temps|espace>` · `/dieu fideles` · `/dieu liberer <joueur>`
· `/dieu choix <trone|refuser>` · `/monde [quitter|visiter <joueur>]` · `/shop [vendre [un]]` · `/argent`
· `/rebellion [declarer|rejoindre]` · `/tournoi [rejoindre|quitter]` · `/trone reclamer <dieu>`
· `/guerre [declarer <dieu>]` · `/classement`

Admins / modérateurs (niveau op 2) :
`/godadmin voir <joueur>` · `essence|argent|gemmes <joueur> <valeur>` · `palier <joueur> <palier>`
· `maitre <joueur> <maître|aucun>` · `reset <joueur>` · `arene` · `mode <serveur|petit>`
· `cooldown <heures>` · `inactivite <jours>` · `netherend <bloquer|autoriser>` · `journal [lignes]`
· `/tournoi creer [prix]` · `/tournoi annuler` · `/pay <enable|disable|status>`

`/godadmin mode petit` adapte le mod à une petite survie (seuils ×0,25, Essence ×2,
rébellion toutes les 12 h, inactivité 2 jours).

Les données sont sauvegardées dans le monde (`world/data/godmod.dat`). Le journal
(`/godadmin journal`) trace conversions, chutes, rébellions, achats premium et actions admin.
