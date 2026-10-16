package it.javalearning.eccezioni.laboratorio.capitolo07;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.UnexpectedRollbackException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Capitolo 07 – Regole di rollback di @Transactional. Ogni metodo del servizio
 * scrive una riga e poi fallisce; il numero di righe rimaste dice se c'è stato
 * commit o rollback. Il test NON è @Transactional: deve osservare il commit reale.
 */
@SpringBootTest
class RollbackTest {

	@Autowired
	ServizioTransazionale servizio;

	@Autowired
	RegistroOperazioniRepository registro;

	@BeforeEach
	void svuota() {
		this.registro.deleteAll();
	}

	@Test
	void checkedNonFaRollback() {
		assertThatThrownBy(() -> this.servizio.scriviPoiLanciaChecked()).isInstanceOf(CheckedDiDominio.class);

		assertThat(this.registro.count()).isEqualTo(1);
	}

	@Test
	void checkedConRollbackForFaRollback() {
		assertThatThrownBy(() -> this.servizio.scriviPoiLanciaCheckedConRollbackFor())
			.isInstanceOf(CheckedDiDominio.class);

		assertThat(this.registro.count()).isZero();
	}

	@Test
	void uncheckedFaRollback() {
		assertThatThrownBy(() -> this.servizio.scriviPoiLanciaUnchecked()).isInstanceOf(IllegalStateException.class);

		assertThat(this.registro.count()).isZero();
	}

	@Test
	void catturareUnaInternaMarcataRollbackOnly() {
		assertThatThrownBy(() -> this.servizio.catturaEccezioneInterna())
			.isInstanceOf(UnexpectedRollbackException.class);

		assertThat(this.registro.count()).isZero();
	}

}
