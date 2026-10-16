package it.javalearning.eccezioni.laboratorio.capitolo07;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Capitolo 07 – Ogni metodo scrive una riga e poi fallisce in modo diverso. */
@Service
public class ServizioTransazionale {

	private final RegistroOperazioniRepository registro;

	private final ServizioInterno interno;

	public ServizioTransazionale(RegistroOperazioniRepository registro, ServizioInterno interno) {
		this.registro = registro;
		this.interno = interno;
	}

	/** Checked senza configurazione: la transazione viene COMMITTATA. */
	@Transactional
	public void scriviPoiLanciaChecked() throws CheckedDiDominio {
		this.registro.save(new RegistroOperazioni("checked"));
		throw new CheckedDiDominio("checked");
	}

	/** Checked con rollbackFor: la transazione viene annullata. */
	@Transactional(rollbackFor = CheckedDiDominio.class)
	public void scriviPoiLanciaCheckedConRollbackFor() throws CheckedDiDominio {
		this.registro.save(new RegistroOperazioni("checked con rollbackFor"));
		throw new CheckedDiDominio("checked con rollbackFor");
	}

	/** Unchecked: rollback (comportamento di default). */
	@Transactional
	public void scriviPoiLanciaUnchecked() {
		this.registro.save(new RegistroOperazioni("unchecked"));
		throw new IllegalStateException("unchecked");
	}

	/**
	 * Cattura l'eccezione di un metodo transazionale interno e prosegue. Ma la
	 * transazione è già rollback-only: al commit Spring lancia
	 * UnexpectedRollbackException e annulla anche la riga scritta qui.
	 */
	@Transactional
	public void catturaEccezioneInterna() {
		this.registro.save(new RegistroOperazioni("esterna"));
		try {
			this.interno.fallisci();
		}
		catch (IllegalStateException ex) {
			// "gestita": si prosegue come se nulla fosse
		}
	}

}
