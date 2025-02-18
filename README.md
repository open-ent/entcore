Notes d'utilisation
====================

Ent-Core est un [ENT](https://fr.wikipedia.org/wiki/Espace_num%C3%A9rique_de_travail) minimaliste,
modulaire et versatile. Il permet d'implémenter aussi bien des ENT de premier, de second degré ou universitaire.

**Remarques** : _Ces notes proposent une démarche rapide de prise en main (typiquement l'installation d'une machine de développement).
Elles ne détaillent pas l'installation des composants techniques (ex : base de données)._

# Installation rapide

## Récupérer le code

Installer le client [Git](http://git-scm.com/) et lancer la commande suivante dans un terminal

	git clone https://github.com/open-ent/entcore.git

## Les composants techniques

Le composants suivants sont utilisés dans Open ENT

* __JDK 21__
* __Maven 3__ (https://maven.apache.org/download.cgi)
* __Neo4j 3.5__ (http://www.neo4j.org/download/linux)
* __MongoDB 7__ (http://docs.mongodb.org/manual/tutorial/install-mongodb-on-debian/)
* __PostgreSQL 14__(https://www.postgresql.org/download/)

Tous les composants peuvent être installés facilement grâce au docker-compose disponible 
dans le projet starter.
