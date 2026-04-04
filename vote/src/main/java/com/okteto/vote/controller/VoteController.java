package com.okteto.vote.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.thymeleaf.util.StringUtils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.UUID;

/**
 * Controlador principal de votación.
 *
 * Aplica el patrón Observer: al recibir un voto, notifica a todos los
 * VoteEventHandler registrados (KafkaVoteHandler, LogVoteHandler), sin
 * acoplar directamente la lógica de Kafka o logging a este controlador.
 */
@Controller
public class VoteController {
    private static final String OPTION_A_ENV_VAR = "OPTION_A";
    private static final String OPTION_B_ENV_VAR = "OPTION_B";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Lista de manejadores (Observers) que se notifican al recibir un voto.
     * Para agregar un nuevo comportamiento (métricas, auditoría, etc.),
     * basta con agregar un nuevo handler a esta lista.
     */
    private List<VoteEventHandler> voteHandlers;

    private List<VoteEventHandler> getHandlers() {
        if (voteHandlers == null) {
            voteHandlers = List.of(
                new KafkaVoteHandler(kafkaTemplate),
                new LogVoteHandler()
            );
        }
        return voteHandlers;
    }

    @GetMapping("/")
    String index(@CookieValue(name = "voter_id", defaultValue = "") String voterId,
                 Model model,
                 HttpServletResponse response) {
        String voter = voterId;
        Vote v = new Vote();
        model.addAttribute("optionA", v.getOptionA());
        model.addAttribute("optionB", v.getOptionB());
        model.addAttribute("hostname", v.getHostname());
        model.addAttribute("vote", null);

        if (StringUtils.isEmpty(voter)) {
            voter = UUID.randomUUID().toString();
        }

        Cookie cookie = new Cookie("voter_id", voter);
        response.addCookie(cookie);

        return "index";
    }

    @PostMapping("/")
    String postForm(@CookieValue(name = "voter_id", defaultValue = "") String voterId,
                    @ModelAttribute Vote voteInput,
                    Model model,
                    HttpServletResponse response) {
        String voter = voterId;
        String vote = voteInput.getVote();
        Vote v = new Vote();
        model.addAttribute("optionA", v.getOptionA());
        model.addAttribute("optionB", v.getOptionB());
        model.addAttribute("hostname", v.getHostname());
        model.addAttribute("vote", vote);

        if (StringUtils.isEmpty(voter)) {
            voter = UUID.randomUUID().toString();
        }

        Cookie cookie = new Cookie("voter_id", voter);
        response.addCookie(cookie);

        // Patrón Observer: notificar a todos los handlers registrados
        final String finalVoter = voter;
        final String finalVote = vote;
        getHandlers().forEach(handler -> handler.onVoteReceived(finalVoter, finalVote));

        return "index";
    }

    public static class Vote {
        private String optionA = "Burritos";
        private String optionB = "Tacos";
        private String hostname = "unknown";
        private String vote;

        public String getOptionA() {
            String result = System.getenv(OPTION_A_ENV_VAR);
            return StringUtils.isEmpty(result) ? this.optionA : result;
        }

        public void setOptionA(String optionA) {
            this.optionA = optionA;
        }

        public String getOptionB() {
            String result = System.getenv(OPTION_B_ENV_VAR);
            return StringUtils.isEmpty(result) ? this.optionB : result;
        }

        public void setOptionB(String optionB) {
            this.optionB = optionB;
        }

        public String getHostname() {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                return this.hostname;
            }
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }

        public String getVote() {
            return vote;
        }

        public void setVote(String vote) {
            this.vote = vote;
        }
    }
}

