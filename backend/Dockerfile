# Krok 7 - PLAN_P1_SPRING_SLICE.md sekce 8, akceptační kritérium
# "docker compose up" -> běží API i databáze bez mountování hostitelských
# cest.
#
# Multi-stage build: stage "build" má celý JDK + Maven a vyrobí fat jar;
# finální image má jen JRE (bez kompilátoru, bez zdrojáků, bez Mavenu) a
# ten jeden jar. [Python -> Java] Nejbližší Python analogie: multi-stage je
# jako mít jeden image s poetry/build nástroji, co vyrobí .whl, a druhý,
# tenký, co má jen runtime a ten .whl nainstalovaný - ne jeden image se
# vším najednou.

# --- stage 1: build ---
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Nejdřív jen wrapper + pom.xml, až pak zdrojáky - Docker cachuje vrstvy
# podle obsahu; když se změní jen Java kód (častý případ), tahle vrstva
# (stažení všech závislostí) se znovu NEPROVÁDÍ, jen se vezme z cache.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src ./src

# -DskipTests: testy tu běžet NEMAJÍ - RaiseSkeletonCalculatorTest/Seg5Test
# by fungovaly (nepotřebují DB), ale SkillRepositoryIT (-Pit profil)
# potřebuje běžící Postgres, který při stavbě image neexistuje, a hlavně -
# test běh patří do "mvn test"/CI (krok 8), ne do každé stavby produkčního
# image. Testy ověřuješ PŘED buildem image, ne během něj.
RUN ./mvnw package -DskipTests -B

# --- stage 2: runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Běžet jako ne-root uživatel - kontejner s rootem uvnitř je zbytečně
# široké oprávnění pro appku, která potřebuje jen naslouchat na portu
# a mluvit s Postgresem.
RUN addgroup -S gamehelper && adduser -S gamehelper -G gamehelper
USER gamehelper

# Wildcard verze - nemusí se měnit při každém bumpu <version> v pom.xml.
COPY --from=build /build/target/game-helper-api-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
