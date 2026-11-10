package com.rpg.concurrency;

import javax.swing.*;

public class PRUEBA extends JFrame {
    private JButton btnClick;
    private JPanel PanelPrincipal;

    public PRUEBA() {
        setTitle("Mi Primer Swing");
        setSize(300, 200);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setContentPane(PanelPrincipal);

        btnClick.addActionListener(e -> sumarDosNumeros());

        setVisible(true);
    }

    private void sumarDosNumeros() {
        String texto1 = JOptionPane.showInputDialog(this, "Escribe el primer numero:", "Suma", JOptionPane.QUESTION_MESSAGE);
        if (texto1 == null) {
            return;
        }
        String texto2 = JOptionPane.showInputDialog(this, "Escribe el segundo numero:", "Suma", JOptionPane.QUESTION_MESSAGE);
        if (texto2 == null) {
            return;
        }

        try {
            double a = Double.parseDouble(texto1.trim().replace(',', '.'));
            double b = Double.parseDouble(texto2.trim().replace(',', '.'));
            double suma = a + b;
            JOptionPane.showMessageDialog(this,
                    formatear(a) + " + " + formatear(b) + " = " + formatear(suma),
                    "Resultado", JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Escribe solo numeros validos.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String formatear(double numero) {
        if (numero == (long) numero) {
            return String.valueOf((long) numero);
        }
        return String.valueOf(numero);
    }

    public static void main(String[] args) {
        new PRUEBA();
    }
}