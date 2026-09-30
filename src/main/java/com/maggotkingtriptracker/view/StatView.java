package com.maggotkingtriptracker.view;

import lombok.Value;

/**
 * A labelled value with its explanation, e.g. the boss-specific cell of the profit card.
 */
@Value
public class StatView
{
	String label;
	String value;
	String help;
}
