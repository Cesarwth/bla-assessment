package com.ballastlane.assessment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** V4 runs on databases that already hold data written by V1-V3: every owned row must end up in its owner's collection. */
class TrainerCollectionsMigrationTest {

    private JdbcTemplate jdbc;
    private Flyway flyway;

    @BeforeEach
    void schemaAtVersion3() throws SQLException {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        Connection connection = DriverManager.getConnection(url, "sa", "");
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(dataSource);
        flyway = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("3").load();
        flyway.migrate();
    }

    private void insertUser(long id, String username) {
        jdbc.update("insert into users (id, username, email, password_hash, role) values (?, ?, ?, 'hash', 'USER')",
                id, username, username + "@pallet.town");
    }

    private void insertPokemon(long id, int pokeApiId, String name, Long ownerId, String nickname, String region) {
        jdbc.update("insert into pokemon (id, pokeapi_id, name, weight_kg, height_m, nickname, region, owner_id, synced_at) "
                + "values (?, ?, ?, 1.0, 1.0, ?, ?, ?, TIMESTAMP WITH TIME ZONE '2026-01-02 03:04:05+00')",
                id, pokeApiId, name, nickname, region, ownerId);
    }

    private void migrateToLatest() {
        Flyway.configure().dataSource(flyway.getConfiguration().getDataSource()).locations("classpath:db/migration").load()
                .migrate();
    }

    @Test
    void ownedRowsBecomeEntriesOfTheirOwnerWithNotesAndOrderedTags() {
        insertUser(1, "ash");
        insertPokemon(10, 25, "pikachu", 1L, "Sparky", "Kanto");
        jdbc.update("insert into pokemon_tags (pokemon_id, sort_order, tag) values (10, 0, 'electric'), (10, 1, 'favorite')");

        migrateToLatest();

        assertThat(jdbc.queryForList("select user_id, pokemon_id, nickname, region, version from trainer_pokemon"))
                .singleElement().satisfies(row -> {
                    assertThat(row.get("USER_ID")).isEqualTo(1L);
                    assertThat(row.get("POKEMON_ID")).isEqualTo(10L);
                    assertThat(row.get("NICKNAME")).isEqualTo("Sparky");
                    assertThat(row.get("REGION")).isEqualTo("Kanto");
                    assertThat(row.get("VERSION")).isEqualTo(0L);
                });
        assertThat(jdbc.queryForList("select tag from trainer_pokemon_tags order by sort_order", String.class))
                .containsExactly("electric", "favorite");
        assertThat(jdbc.queryForObject("select count(*) from trainer_pokemon where added_at = "
                + "TIMESTAMP WITH TIME ZONE '2026-01-02 03:04:05+00'", Integer.class)).isEqualTo(1);
    }

    @Test
    void ownerlessRowsStayInTheCatalogOnly() {
        insertUser(1, "ash");
        insertPokemon(10, 25, "pikachu", 1L, null, null);
        insertPokemon(11, 26, "raichu", null, "Orphan", "Kanto");

        migrateToLatest();

        assertThat(jdbc.queryForObject("select count(*) from pokemon", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForList("select pokemon_id from trainer_pokemon", Long.class)).containsExactly(10L);
    }

    @Test
    void theCatalogLosesTheTrainerSpecificColumnsAndTables() {
        migrateToLatest();

        List<String> columns = jdbc.queryForList(
                "select lower(column_name) from information_schema.columns where lower(table_name) = 'pokemon'", String.class);
        assertThat(columns).doesNotContain("nickname", "region", "owner_id").contains("version", "pokeapi_id", "synced_at");
        assertThat(jdbc.queryForObject(
                "select count(*) from information_schema.tables where lower(table_name) = 'pokemon_tags'", Integer.class))
                .isZero();
    }

    @Test
    void aTrainerCannotHaveTheSamePokemonTwiceAndDeletingTheUserCascades() {
        migrateToLatest();
        insertUser(1, "ash");
        insertPokemon10();
        jdbc.update("insert into trainer_pokemon (user_id, pokemon_id, added_at) values (1, 10, CURRENT_TIMESTAMP)");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                "insert into trainer_pokemon (user_id, pokemon_id, added_at) values (1, 10, CURRENT_TIMESTAMP)"))
                .hasMessageContaining("UQ_TRAINER_POKEMON");

        jdbc.update("delete from users where id = 1");
        assertThat(jdbc.queryForObject("select count(*) from trainer_pokemon", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pokemon", Integer.class)).isEqualTo(1);
    }

    // After V4 the old columns are gone, so the catalog row is inserted with the new, smaller shape.
    private void insertPokemon10() {
        jdbc.update("insert into pokemon (id, pokeapi_id, name, weight_kg, height_m, synced_at) "
                + "values (10, 25, 'pikachu', 1.0, 1.0, CURRENT_TIMESTAMP)");
    }
}
