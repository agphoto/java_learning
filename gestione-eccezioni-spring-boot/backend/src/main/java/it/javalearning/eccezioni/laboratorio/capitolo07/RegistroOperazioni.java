package it.javalearning.eccezioni.laboratorio.capitolo07;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/** Una riga scritta da ogni operazione: se resta nel database c'è stato commit. */
@Entity
public class RegistroOperazioni {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String descrizione;

	protected RegistroOperazioni() {
	}

	public RegistroOperazioni(String descrizione) {
		this.descrizione = descrizione;
	}

	public Long getId() {
		return this.id;
	}

	public String getDescrizione() {
		return this.descrizione;
	}

}
