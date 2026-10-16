package it.javalearning.eccezioni.laboratorio.capitolo07;

/** Un'eccezione checked: per default NON provoca rollback. */
public class CheckedDiDominio extends Exception {

	public CheckedDiDominio(String message) {
		super(message);
	}

}
