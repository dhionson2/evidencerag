package dev.evidencerag.api;

import dev.evidencerag.application.RagQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/query")
public class QueryController {
    private final RagQueryService rag;
    public QueryController(RagQueryService rag) { this.rag = rag; }
    @PostMapping
    public ApiDtos.QueryResponse query(@Valid @RequestBody ApiDtos.QueryRequest request) {
        return ApiDtos.QueryResponse.from(rag.ask(request.question().trim()));
    }
}
