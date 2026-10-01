package com.bosstriptracker.boss;

import org.junit.Test;

public class LootCategoriesTest
{
	@Test(expected = IllegalArgumentException.class)
	public void anItemCanOnlyBeInOneCategory()
	{
		LootCategories.builder()
			.put(LootCategories.UNIQUES, 1)
			.put(LootCategories.OTHER, 1)
			.build();
	}
}
