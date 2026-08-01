package ch.interlis.generator.metadata.selection;

import ch.interlis.ili2c.metamodel.Element;
import ch.interlis.ili2c.metamodel.Model;
import ch.interlis.ili2c.metamodel.PredefinedModel;
import ch.interlis.ili2c.metamodel.TransferDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Leitet die {@link ModelSelection} aus der echten ili2c-TransferDescription ab.
 *
 * <p>Traversierung: Root wird exakt über {@code TransferDescription.getElement(rootModelName)}
 * aufgelöst (nicht gefunden ist ein Fehler). Direkte Imports werden über die stabile
 * ili2c-API {@code Model.getImporting()} gelesen (liefert in ili2c 5.6.8 die von diesem
 * Modell importierten Modelle) und transitiv traversiert. Der Cycle-Schutz läuft über
 * die qualifizierten Modellnamen. Predefined-/Type-Modelle (z.&nbsp;B. {@code INTERLIS})
 * werden nicht als physische Zielmodelle aufgenommen.</p>
 */
public final class ModelSelectionResolver {

    private static final Logger logger = LoggerFactory.getLogger(ModelSelectionResolver.class);

    /**
     * Bestimmt die Auswahl aus der TransferDescription.
     *
     * @throws IllegalArgumentException wenn das Root-Modell nicht gefunden wird
     */
    public ModelSelection fromTransferDescription(
            TransferDescription td,
            String rootModelName) {
        Objects.requireNonNull(td, "td");
        Objects.requireNonNull(rootModelName, "rootModelName");

        Model root = resolveRoot(td, rootModelName);

        Set<String> included = new LinkedHashSet<>();
        included.add(canonicalName(root));
        if (!rootModelName.equals(canonicalName(root))) {
            included.add(rootModelName);
        }
        transitiveImports(root).stream()
            .sorted()
            .forEach(included::add);

        return new ModelSelection(
            rootModelName,
            included,
            ModelSelectionSource.ILI2C_DEPENDENCY_GRAPH
        );
    }

    /**
     * Liefert die transitiven Importe eines Modells in stabiler lexikografischer
     * Reihenfolge (ohne das Root selbst, ohne Predefined-Modelle).
     */
    Set<String> transitiveImports(Model root) {
        Set<String> visited = new LinkedHashSet<>();
        Deque<Model> queue = new ArrayDeque<>();
        queue.add(root);
        visited.add(canonicalName(root));

        while (!queue.isEmpty()) {
            Model current = queue.removeFirst();
            for (Model imported : current.getImporting()) {
                if (imported instanceof PredefinedModel) {
                    continue;
                }
                String name = canonicalName(imported);
                if (name == null || !visited.add(name)) {
                    continue;
                }
                queue.addLast(imported);
            }
        }

        List<String> dependencies = new ArrayList<>(visited);
        dependencies.remove(canonicalName(root));
        return new LinkedHashSet<>(dependencies);
    }

    private Model resolveRoot(TransferDescription td, String rootModelName) {
        Element element = td.getElement(rootModelName);
        if (!(element instanceof Model model)) {
            throw new IllegalArgumentException(
                "Model not found: " + rootModelName);
        }
        return model;
    }

    private String canonicalName(Model model) {
        if (model.getScopedName(null) != null) {
            return model.getScopedName(null);
        }
        return model.getName();
    }
}
