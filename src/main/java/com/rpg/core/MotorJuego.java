package com.rpg.core;

import com.rpg.concurrency.AutoGuardadoDaemon;
import com.rpg.concurrency.GestorTurno;
import com.rpg.entities.Enemigo;
import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Gremio;
import com.rpg.entities.Guerrero;
import com.rpg.entities.Mago;
import com.rpg.entities.PerfilJugador;
import com.rpg.entities.Personaje;
import com.rpg.entities.enums.RaridadItem;
import com.rpg.entities.enums.TipoEfecto;
import com.rpg.exceptions.CapacidadExcedidaException;
import com.rpg.items.Inventario;
import com.rpg.items.Pocion;

import java.util.List;
import java.util.Random;

/** Coordina el flujo del juego: crear heroe, combatir, torneo, bestiario y guardado. */
public class MotorJuego {

    private static final String NOMBRE_GREMIO = "Guardianes del Alba";
    private static final int CURACION_POCION_VIDA = 30;
    private static final int SEGUNDOS_TURNO = 15;
    private static final long INTERVALO_AUTOGUARDADO_MS = 10_000;

    private final Consola consola;
    private final RegistroCombate registro = new RegistroCombate();
    private final Arena arena = new Arena(registro, new Random());
    private final Bestiario bestiario = new Bestiario();
    private final PerfilJugador perfil = new PerfilJugador();
    private final Gremio gremio = new Gremio(NOMBRE_GREMIO);
    private final TorneoManager torneo = new TorneoManager(arena, registro);
    private final GestorTurno gestorTurno = new GestorTurno();
    private volatile boolean tiempoAgotado;
    // Datos que comparten el hilo principal y el hilo de auto-guardado
    private volatile Personaje jugadorActivo;
    private volatile Enemigo enemigoActivo;
    private volatile String ultimaCopiaAuto = "";
    private int contadorAutoGuardado;
    // TALLER ARCHIVOS: ultimoGuardado es solo el estado de la partida en memoria. Aqui (o en un metodo
    // nuevo como cargarProgreso) iria la carga de una partida guardada en disco; llamala desde Main.
    private String ultimoGuardado = "";

    public MotorJuego(Consola consola) {
        this.consola = consola;
    }

    // ---- opciones del menu principal

    public void combateRapido() {
        registro.limpiar();
        Personaje jugador = crearJugador();
        Enemigo enemigo = elegirEnemigo();
        jugadorActivo = jugador;
        enemigoActivo = enemigo;
        registro.agregar("%n=== %s vs %s ===", jugador.getNombre(), enemigo.getNombre());

        boolean huyo = combatir(jugador, enemigo);

        cerrarCombate(jugador, enemigo, huyo);
        guardarProgreso();
    }

    public void jugarTorneo() {
        registro.limpiar();
        Personaje campeon = torneo.jugarTorneo();
        registro.agregar("%nCAMPEON DEL TORNEO: %s", campeon.getNombre());
        perfil.desbloquearLogro("Campeon del Torneo");
        guardarProgreso();
    }

    public void mostrarBestiario() {
        System.out.println("\n=== BESTIARIO ===");
        System.out.print(bestiario.describir());
        System.out.printf("Victorias: %d | Derrotas: %d | Logros: %s%n",
                perfil.getVictorias(), perfil.getDerrotas(), perfil.getLogros());
    }

    // ---- creacion de personajes

    private Personaje crearJugador() {
        int clase = consola.leerEntero("Clase (1. Guerrero, 2. Mago): ", 1, 2);
        String nombre = consola.leerNombre("Nombre del heroe: ");
        Personaje jugador = switch (clase) {
            case 1 -> new Guerrero(nombre);
            default -> new Mago(nombre);
        };
        entregarKitInicial(jugador);
        jugador.unirseAGremio(gremio);
        System.out.printf("%s se une al gremio %s (%d miembros).%n",
                jugador.getNombre(), gremio.getNombre(), gremio.getMiembros().size());
        return jugador;
    }

    private void entregarKitInicial(Personaje jugador) {
        List<Pocion> kit = List.of(
                new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN),
                new Pocion("Elixir de Regeneracion", 0, RaridadItem.RARO,
                        new EstadoEfecto("Regeneracion", TipoEfecto.CURACION_CONTINUA, 6, 3), false),
                new Pocion("Antidoto", 0, RaridadItem.COMUN, null, true));
        try {
            for (Pocion pocion : kit) {
                jugador.getInventario().agregar(pocion);
            }
        } catch (CapacidadExcedidaException e) {
            System.out.println("No se pudo entregar todo el kit: " + e.getMessage());
        }
    }

    private Enemigo elegirEnemigo() {
        List<String> nombres = bestiario.nombres();
        System.out.println("Rivales disponibles:");
        for (int i = 0; i < nombres.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, nombres.get(i));
        }
        while (true) {
            String texto = consola.leerLinea("Rival (numero o nombre): ");
            Enemigo enemigo = resolverRival(texto, nombres);
            if (enemigo != null) {
                return enemigo;
            }
            System.out.println("No existe ese rival.");
        }
    }

    private Enemigo resolverRival(String texto, List<String> nombres) {
        try {
            int numero = Integer.parseInt(texto);
            boolean valido = numero >= 1 && numero <= nombres.size();
            return valido ? bestiario.crearEnemigo(nombres.get(numero - 1)) : null;
        } catch (NumberFormatException e) {
            return bestiario.crearEnemigo(texto);
        }
    }

    // ---- bucle de combate

    /** @return true si el jugador huyo */
    private boolean combatir(Personaje jugador, Enemigo enemigo) {
        int ronda = 1;
        while (jugador.estaVivo() && enemigo.estaVivo()) {
            registro.agregar("%n-- Ronda %d --", ronda);
            if (arena.iniciarTurno(jugador) && turnoJugador(jugador, enemigo)) {
                return true;
            }
            arena.turnoAutomatico(enemigo, jugador);
            ronda++;
        }
        return false;
    }

    /**
     * Turno del jugador. Las acciones que no gastan turno (ver estado, cancelar) vuelven a pedir.
     * @return true si el jugador decidio huir
     */
    private boolean turnoJugador(Personaje jugador, Enemigo enemigo) {
        boolean turnoGastado;
        do {
            registro.agregar(FormatoCombate.resumen(jugador));
            registro.agregar(FormatoCombate.resumen(enemigo));
            Accion accion = pedirAccion();
            if (accion == null) {
                registro.agregar("%s tardo demasiado y pierde el turno.", jugador.getNombre());
                return false;
            }
            if (accion == Accion.HUIR) {
                return true;
            }
            turnoGastado = switch (accion) {
                case ATACAR -> {
                    arena.atacar(jugador, enemigo);
                    yield true;
                }
                case ESPECIAL -> arena.intentarEspecial(jugador, enemigo);
                case POCION -> usarPocion(jugador);
                default -> {
                    mostrarEstado(jugador);
                    yield false;
                }
            };
        } while (!turnoGastado && enemigo.estaVivo());
        return false;
    }

    /**
     * Pide la accion al jugador con un limite de tiempo. Si el tiempo se agota devuelve null
     * y el turno se pierde.
     */
    private Accion pedirAccion() {
        tiempoAgotado = false;
        gestorTurno.iniciar(SEGUNDOS_TURNO, () -> {
            tiempoAgotado = true;
            System.out.println("\nTiempo agotado! Escribe un numero valido y pulsa Enter, perderas el turno.");
        });
        int opcion;
        try {
            System.out.println("1. Atacar  2. Habilidad especial  3. Usar pocion  4. Ver estado  0. Huir");
            opcion = consola.leerEntero("Accion: ", 0, 4);
        } finally {
            gestorTurno.cancelar();
        }
        if (tiempoAgotado) {
            return null;
        }
        return switch (opcion) {
            case 1 -> Accion.ATACAR;
            case 2 -> Accion.ESPECIAL;
            case 3 -> Accion.POCION;
            case 4 -> Accion.ESTADO;
            default -> Accion.HUIR;
        };
    }

    private boolean usarPocion(Personaje jugador) {
        Inventario<Pocion> inventario = jugador.getInventario();
        if (inventario.estaVacio()) {
            System.out.println("No tienes pociones.");
            return false;
        }
        int numero = 1;
        for (Pocion pocion : inventario) {
            System.out.printf("  %d. %s%n", numero++, pocion.getNombre());
        }
        int eleccion = consola.leerEntero("Pocion (0 = cancelar): ", 0, inventario.tamanio());
        if (eleccion == 0) {
            return false;
        }
        registro.agregar(jugador.usarPocion(inventario.quitar(eleccion - 1)));
        return true;
    }

    private void mostrarEstado(Personaje jugador) {
        System.out.printf("Habilidades: %s | Arma: %s | Defensa: %d | Pociones: %d%n",
                jugador.getHabilidades(),
                jugador.getArma() != null ? jugador.getArma().getNombre() : "ninguna",
                jugador.getDefensa(), jugador.getInventario().tamanio());
        for (EstadoEfecto efecto : jugador.getEfectos()) {
            System.out.printf("Efecto activo: %s (%d turnos)%n", efecto.getNombre(), efecto.getTurnosRestantes());
        }
    }

    // ---- resultado del combate

    private void cerrarCombate(Personaje jugador, Enemigo enemigo, boolean huyo) {
        jugador.cancelarEfectos();
        enemigo.cancelarEfectos();
        if (huyo) {
            registro.agregar("%s huye del combate.", jugador.getNombre());
        } else if (jugador.estaVivo()) {
            registro.agregar("%nVictoria de %s!", jugador.getNombre());
            perfil.registrarVictoria();
            bestiario.registrarEnemigoDerrotado(enemigo.getNombre());
            desbloquearLogros(jugador, enemigo);
            repartirBotin(jugador);
        } else {
            registro.agregar("%n%s ha caido ante %s.", jugador.getNombre(), enemigo.getNombre());
            perfil.registrarDerrota();
        }
    }

    private void desbloquearLogros(Personaje jugador, Enemigo enemigo) {
        boolean intacto = jugador.getVida() == jugador.getVidaMaxima();
        List<String> candidatos = List.of("Primera Victoria", "Cazador de " + enemigo.getNombre(),
                intacto ? "Intocable" : "");
        for (String logro : candidatos) {
            if (!logro.isEmpty() && perfil.desbloquearLogro(logro)) {
                registro.agregar("Logro desbloqueado: %s", logro);
            }
        }
    }

    private void repartirBotin(Personaje jugador) {
        try {
            jugador.getInventario().agregar(new Pocion("Pocion de Vida", CURACION_POCION_VIDA, RaridadItem.COMUN));
            registro.agregar("Botin: Pocion de Vida.");
        } catch (CapacidadExcedidaException e) {
            registro.agregar("Botin perdido: %s", e.getMessage());
        }
    }

    // ---- guardado

    /**
     * Guarda las estadisticas en memoria (el texto queda en {@code ultimoGuardado}); aun no se escribe ningun
     * archivo. Hoy solo se invoca al terminar una pelea.
     */
    public void guardarProgreso() {
        // TALLER ARCHIVOS: ademas de dejar el texto en memoria, escribe la partida en disco (por ejemplo un
        // .txt con BufferedWriter, y/o un .dat serializando el estado con ObjectOutputStream). Usa try-with-resources.
        // TALLER CONCURRENCIA: este guardado es manual. Debera hacerse solo, de fondo, mientras se juega.
        StringBuilder contenido = new StringBuilder();
        contenido.append("=== PARTIDA RPG ===").append(System.lineSeparator());
        contenido.append("Victorias: ").append(perfil.getVictorias()).append(System.lineSeparator());
        contenido.append("Derrotas: ").append(perfil.getDerrotas()).append(System.lineSeparator());
        contenido.append("Logros: ").append(String.join(", ", perfil.getLogros())).append(System.lineSeparator());
        contenido.append("Gremio ").append(gremio.getNombre()).append(": ")
                .append(gremio.getMiembros().size()).append(" miembros").append(System.lineSeparator());
        contenido.append("Enemigos derrotados: ").append(bestiario.resumenDerrotados()).append(System.lineSeparator());
        contenido.append(System.lineSeparator()).append("--- Ultimo combate ---").append(System.lineSeparator());
        contenido.append(registro.getTexto());
        ultimoGuardado = contenido.toString();
        System.out.println("[Progreso guardado en memoria]");
    }

    public String getUltimoGuardado() {
        return ultimoGuardado;
    }

    // ---- auto-guardado en segundo plano (hilo daemon)

    /** Arranca el auto-guardado: un hilo daemon que no impide que el programa termine. */
    public void iniciarAutoGuardado() {
        Thread hilo = new Thread(new AutoGuardadoDaemon(INTERVALO_AUTOGUARDADO_MS, this::autoGuardar), "auto-guardado");
        hilo.setDaemon(true); // siempre antes de start(); si no, lanza IllegalThreadStateException
        hilo.start();
    }

    /** Guarda en memoria una copia con las estadisticas de los personajes y el historial de la partida. */
    private void autoGuardar() {
        StringBuilder copia = new StringBuilder();
        copia.append("=== AUTO-GUARDADO #").append(++contadorAutoGuardado).append(" ===").append(System.lineSeparator());
        copia.append("Victorias: ").append(perfil.getVictorias())
                .append(" | Derrotas: ").append(perfil.getDerrotas()).append(System.lineSeparator());
        agregarEstadisticas(copia, jugadorActivo);
        agregarEstadisticas(copia, enemigoActivo);
        copia.append("--- Historial ---").append(System.lineSeparator());
        copia.append(registro.getTexto());
        ultimaCopiaAuto = copia.toString();
        System.out.println("[Auto-guardado #" + contadorAutoGuardado + "]");
    }

    private void agregarEstadisticas(StringBuilder copia, Personaje personaje) {
        if (personaje == null) {
            return;
        }
        copia.append(personaje.getNombre()).append(": vida ").append(personaje.getVida())
                .append("/").append(personaje.getVidaMaxima())
                .append(" | estado ").append(personaje.getEstado()).append(System.lineSeparator());
    }

    public String getUltimaCopiaAuto() {
        return ultimaCopiaAuto;
    }
}