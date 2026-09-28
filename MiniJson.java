package BCraftOSproject1.BCraftOS1;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitor de JSON mínimo, sem biblioteca externa (o projeto é Java puro).
 * Devolve Map (objeto), List (lista), String, Double, Boolean ou null.
 */
public class MiniJson {

	private final String texto;
	private int pos;

	private MiniJson(String texto) {
		this.texto = texto;
	}

	public static Object ler(String json) {
		MiniJson leitor = new MiniJson(json);
		leitor.pularEspacos();
		Object valor = leitor.lerValor();
		leitor.pularEspacos();
		if (leitor.pos != leitor.texto.length()) {
			throw new IllegalArgumentException("JSON com texto sobrando na posição " + leitor.pos);
		}
		return valor;
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Object> objeto(Object valor) {
		return valor instanceof Map ? (Map<String, Object>) valor : null;
	}

	@SuppressWarnings("unchecked")
	public static List<Object> lista(Object valor) {
		return valor instanceof List ? (List<Object>) valor : new ArrayList<>();
	}

	public static String texto(Object valor) {
		return valor instanceof String ? (String) valor : null;
	}

	private Object lerValor() {
		if (pos >= texto.length()) {
			throw new IllegalArgumentException("JSON terminou antes da hora");
		}
		char c = texto.charAt(pos);
		switch (c) {
			case '{':
				return lerObjeto();
			case '[':
				return lerLista();
			case '"':
				return lerTexto();
			case 't':
				pos += 4;
				return Boolean.TRUE;
			case 'f':
				pos += 5;
				return Boolean.FALSE;
			case 'n':
				pos += 4;
				return null;
			default:
				return lerNumero();
		}
	}

	private Map<String, Object> lerObjeto() {
		Map<String, Object> mapa = new LinkedHashMap<>();
		pos++; // {
		pularEspacos();
		if (texto.charAt(pos) == '}') {
			pos++;
			return mapa;
		}
		while (true) {
			pularEspacos();
			String chave = lerTexto();
			pularEspacos();
			pos++; // :
			pularEspacos();
			mapa.put(chave, lerValor());
			pularEspacos();
			char c = texto.charAt(pos++);
			if (c == '}') {
				return mapa;
			}
			if (c != ',') {
				throw new IllegalArgumentException("JSON inválido na posição " + (pos - 1));
			}
		}
	}

	private List<Object> lerLista() {
		List<Object> itens = new ArrayList<>();
		pos++; // [
		pularEspacos();
		if (texto.charAt(pos) == ']') {
			pos++;
			return itens;
		}
		while (true) {
			pularEspacos();
			itens.add(lerValor());
			pularEspacos();
			char c = texto.charAt(pos++);
			if (c == ']') {
				return itens;
			}
			if (c != ',') {
				throw new IllegalArgumentException("JSON inválido na posição " + (pos - 1));
			}
		}
	}

	private String lerTexto() {
		StringBuilder sb = new StringBuilder();
		pos++; // aspas de abertura
		while (true) {
			char c = texto.charAt(pos++);
			if (c == '"') {
				return sb.toString();
			}
			if (c == '\\') {
				char e = texto.charAt(pos++);
				switch (e) {
					case 'n': sb.append('\n'); break;
					case 't': sb.append('\t'); break;
					case 'r': sb.append('\r'); break;
					case 'b': sb.append('\b'); break;
					case 'f': sb.append('\f'); break;
					case 'u':
						sb.append((char) Integer.parseInt(texto.substring(pos, pos + 4), 16));
						pos += 4;
						break;
					default: sb.append(e);
				}
			} else {
				sb.append(c);
			}
		}
	}

	private Double lerNumero() {
		int inicio = pos;
		while (pos < texto.length() && "+-0123456789.eE".indexOf(texto.charAt(pos)) >= 0) {
			pos++;
		}
		return Double.valueOf(texto.substring(inicio, pos));
	}

	private void pularEspacos() {
		while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) {
			pos++;
		}
	}
}
