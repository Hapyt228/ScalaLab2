package org.example.gilb;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Controller
public class GilbController {

    private final GilbAnalyzer analyzer;

    public GilbController(GilbAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @GetMapping("/")
    public String index(Model model) throws IOException {
        model.addAttribute("source", loadSample());
        return "index";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam("source") String source, Model model) {
        model.addAttribute("source", source);
        model.addAttribute("result", analyzer.analyze(source));
        return "index";
    }

    private String loadSample() throws IOException {
        try (InputStream in = new ClassPathResource("sample/Stats.scala").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}