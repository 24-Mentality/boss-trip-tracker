package com.bosstriptracker.view;

import java.util.List;
import lombok.Value;

/**
 * What one type of tarnished item has polished into.
 */
@Value
public class PolishView
{
	int tarnishedItemId;
	String tarnishedName;
	int total;
	List<ItemView> outcomes;
}
