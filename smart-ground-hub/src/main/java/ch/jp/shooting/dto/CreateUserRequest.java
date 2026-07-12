package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.Nullable;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@org.jspecify.annotations.NullMarked
public class CreateUserRequest {
    @NotBlank @Email
    private String email;
    @NotBlank
    private String username;
    @NotBlank
    private String password;
    @NotBlank
    private String vorname;
    @NotBlank
    private String nachname;

    @Nullable
    private LocalDate geburtsdatum;

    @Nullable
    private String geschlecht;

    @Nullable
    private String telefonnummer;

    @Nullable
    private String strasse;

    @Nullable
    private String hausnummer;

    @Nullable
    private String plz;

    @Nullable
    private String stadt;

    @Nullable
    private String land;

    @Nullable
    private String mitgliedsnummer;

    @Nullable
    private String sprache;

    // ==================== CONSTRUCTORS ====================
    public CreateUserRequest() {}

    public CreateUserRequest(String email, String password, String vorname, String nachname) {
        this.email = email;
        this.password = password;
        this.vorname = vorname;
        this.nachname = nachname;
    }

    // ==================== GETTERS & SETTERS ====================
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getVorname() { return vorname; }
    public void setVorname(String vorname) { this.vorname = vorname; }

    public String getNachname() { return nachname; }
    public void setNachname(String nachname) { this.nachname = nachname; }

    @Nullable
    public LocalDate getGeburtsdatum() { return geburtsdatum; }
    public void setGeburtsdatum(@Nullable LocalDate geburtsdatum) { this.geburtsdatum = geburtsdatum; }

    @Nullable
    public String getGeschlecht() { return geschlecht; }
    public void setGeschlecht(@Nullable String geschlecht) { this.geschlecht = geschlecht; }

    @Nullable
    public String getTelefonnummer() { return telefonnummer; }
    public void setTelefonnummer(@Nullable String telefonnummer) { this.telefonnummer = telefonnummer; }

    @Nullable
    public String getStrasse() { return strasse; }
    public void setStrasse(@Nullable String strasse) { this.strasse = strasse; }

    @Nullable
    public String getHausnummer() { return hausnummer; }
    public void setHausnummer(@Nullable String hausnummer) { this.hausnummer = hausnummer; }

    @Nullable
    public String getPlz() { return plz; }
    public void setPlz(@Nullable String plz) { this.plz = plz; }

    @Nullable
    public String getStadt() { return stadt; }
    public void setStadt(@Nullable String stadt) { this.stadt = stadt; }

    @Nullable
    public String getLand() { return land; }
    public void setLand(@Nullable String land) { this.land = land; }

    @Nullable
    public String getMitgliedsnummer() { return mitgliedsnummer; }
    public void setMitgliedsnummer(@Nullable String mitgliedsnummer) { this.mitgliedsnummer = mitgliedsnummer; }

    @Nullable
    public String getSprache() { return sprache; }
    public void setSprache(@Nullable String sprache) { this.sprache = sprache; }
}
