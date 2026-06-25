package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
@org.jspecify.annotations.NullMarked
public class UpdateUserRequest {

    @Nullable
    private String email;

    @Nullable
    private String username;

    @Nullable
    private String vorname;

    @Nullable
    private String nachname;

    @Nullable
    private LocalDate geburtsdatum;

    @Nullable
    private String geschlecht;

    @Nullable
    private String telefonnummer;

    @Nullable
    private Boolean telefonBestaetigt;

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
    private String profilbildUrl;

    @Nullable
    private String biographie;

    @Nullable
    private String sprache;

    @Nullable
    private String mitgliedsnummer;

    @Nullable
    private String schiessLizenz;

    @Nullable
    private LocalDate schiessLizenzVerfallsdatum;

    @Nullable
    private String status;

    // ==================== CONSTRUCTORS ====================
    public UpdateUserRequest() {}

    // ==================== GETTERS & SETTERS ====================

    @Nullable
    public String getEmail() { return email; }
    public void setEmail(@Nullable String email) { this.email = email; }

    @Nullable
    public String getUsername() { return username; }
    public void setUsername(@Nullable String username) { this.username = username; }

    @Nullable
    public String getVorname() { return vorname; }
    public void setVorname(@Nullable String vorname) { this.vorname = vorname; }

    @Nullable
    public String getNachname() { return nachname; }
    public void setNachname(@Nullable String nachname) { this.nachname = nachname; }

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
    public Boolean getTelefonBestaetigt() { return telefonBestaetigt; }
    public void setTelefonBestaetigt(@Nullable Boolean telefonBestaetigt) { this.telefonBestaetigt = telefonBestaetigt; }

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
    public String getProfilbildUrl() { return profilbildUrl; }
    public void setProfilbildUrl(@Nullable String profilbildUrl) { this.profilbildUrl = profilbildUrl; }

    @Nullable
    public String getBiographie() { return biographie; }
    public void setBiographie(@Nullable String biographie) { this.biographie = biographie; }

    @Nullable
    public String getSprache() { return sprache; }
    public void setSprache(@Nullable String sprache) { this.sprache = sprache; }

    @Nullable
    public String getMitgliedsnummer() { return mitgliedsnummer; }
    public void setMitgliedsnummer(@Nullable String mitgliedsnummer) { this.mitgliedsnummer = mitgliedsnummer; }

    @Nullable
    public String getSchiessLizenz() { return schiessLizenz; }
    public void setSchiessLizenz(@Nullable String schiessLizenz) { this.schiessLizenz = schiessLizenz; }

    @Nullable
    public LocalDate getSchiessLizenzVerfallsdatum() { return schiessLizenzVerfallsdatum; }
    public void setSchiessLizenzVerfallsdatum(@Nullable LocalDate schiessLizenzVerfallsdatum) {
        this.schiessLizenzVerfallsdatum = schiessLizenzVerfallsdatum;
    }

    @Nullable
    public String getStatus() { return status; }
    public void setStatus(@Nullable String status) { this.status = status; }
}
