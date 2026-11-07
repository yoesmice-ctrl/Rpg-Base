package com.rpg.concurrency;

import com.rpg.entities.EstadoEfecto;
import com.rpg.entities.Personaje;

import java.util.function.Consumer;

/** Efecto continuo (DoT o HoT) que corre en su propio hilo y se cancela con interrupt(). */
public class EfectoContinuoTask implements Runnable {

    private final Personaje objetivo;
    private final EstadoEfecto efecto;
    private final long intervaloMs;
    private final Consumer<String> salida;

    public EfectoContinuoTask(Personaje objetivo, EstadoEfecto efecto, long intervaloMs, Consumer<String> salida) {
        this.objetivo = objetivo;
        this.efecto = efecto;
        this.intervaloMs = intervaloMs;
        this.salida = salida;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted() && !efecto.haTerminado() && objetivo.estaVivo()) {
                try {
                    Thread.sleep(intervaloMs);
                } catch (InterruptedException e) {
                    // Cancelado (antidoto, purificacion o fin del combate): se conserva la interrupcion y se sale
                    Thread.currentThread().interrupt();
                    break;
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
                salida.accept(aplicarTick());
                efecto.consumirTurno();
            }
        } finally {
            // Se ejecuta siempre: tanto si el efecto termino solo como si lo cancelaron
            boolean terminoSolo = efecto.haTerminado();
            objetivo.efectoFinalizado(efecto);
            if (terminoSolo) {
                salida.accept(objetivo.getNombre() + ": termina " + efecto.getNombre());
            }
        }
    }

    private String aplicarTick() {
        if (efecto.esDanio()) {
            objetivo.recibirDanio(efecto.getValorPorTick());
            return String.format("%s sufre %d de dano por %s",
                    objetivo.getNombre(), efecto.getValorPorTick(), efecto.getNombre());
        }
        int curado = objetivo.curar(efecto.getValorPorTick());
        return String.format("%s recupera %d de vida por %s", objetivo.getNombre(), curado, efecto.getNombre());
    }
}