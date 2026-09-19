package villager_customers.model;

/**
 * A plain item-and-count shape, identified by registry id string rather than any live game object,
 * so {@link MatchRule} and {@link NuggetConversion} stay free of Minecraft imports (`TRANSACTION-REQ-001`).
 *
 * @param itemId the item's registry id, e.g. {@code "minecraft:wheat"}
 * @param count the stack's count
 */
public record StackShape(String itemId, int count) {
}
