package com.bosstriptracker.view;

import java.util.List;
import lombok.Value;

/**
 * One group of a loot box (e.g. Uniques or Resources), with its items for the tooltip.
 */
@Value
public class LootCategory
{
	String name;
	long value;
	/**
	 * Highest value first.
	 */
	List<ItemView> items;
}
