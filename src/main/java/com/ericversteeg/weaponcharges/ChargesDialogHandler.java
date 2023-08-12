//TODO: attribute geheur
package com.ericversteeg.weaponcharges;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ChargesDialogHandler
{
	@FunctionalInterface
	public interface MatchHandler
	{
		void handleDialog(DialogStateMatcher.DialogStateMatchers matchers, DialogTracker.DialogState dialogState, String optionSelected, WeaponChargesManager manager);
	}

	private final DialogStateMatcher dialogStateMatcher;
	private final MatchHandler matchHandler;

	public boolean handleDialog(DialogTracker.DialogState dialogState, WeaponChargesManager manager)
	{
		DialogStateMatcher.DialogStateMatchers matchers = dialogStateMatcher.matchDialog(dialogState);
		boolean matched = matchers != null;
		if (matched)
		{
			matchHandler.handleDialog(matchers, dialogState, null, manager);
		}
		return matched;
	}

	public boolean handleDialogOptionSelected(DialogTracker.DialogState dialogState, String optionSelected, WeaponChargesManager manager)
	{
		DialogStateMatcher.DialogStateMatchers matchers = dialogStateMatcher.matchDialogOptionSelected(dialogState, optionSelected);
		boolean matched = matchers != null;
		if (matched)
		{
			matchHandler.handleDialog(matchers, dialogState, optionSelected, manager);
		}
		return matched;
	}

	public static MatchHandler genericSpriteDialogChargesMessage(boolean chargesAbsolute, int group) {
		return (matchers, dialogState, optionSelected, manager) -> {
			if (dialogState.spriteDialogItemId == null) throw new IllegalArgumentException("This handler is for sprite dialogs only.");

			String chargeCountString = matchers.getTextMatcher().group(group).replaceAll(",", "");
			int charges = Integer.parseInt(chargeCountString);
			ChargedWeapon chargedWeapon = ChargedWeapon.getChargedWeaponFromId(matchers.getSpriteDialogId());
			if (chargedWeapon != null)
			{
				if (chargesAbsolute)
				{
					manager.setCharges(chargedWeapon, charges);
				} else {
					manager.addCharges(chargedWeapon, charges, true);
				}
			}
		};
	}

	public static MatchHandler genericSpriteDialogUnchargeMessage()
	{
		return (matchers, dialogState, optionSelected, manager) -> {
			if (dialogState.spriteDialogItemId == null) throw new IllegalArgumentException("This handler is for sprite dialogs only.");

			ChargedWeapon chargedWeapon = ChargedWeapon.getChargedWeaponFromId(matchers.getSpriteDialogId());
			if (chargedWeapon != null)
			{
				manager.setCharges(chargedWeapon, 0);
			}
		};
	}

	public static MatchHandler genericSpriteDialogFullChargeMessage()
	{
		return (matchers, dialogState, optionSelected, manager) -> {
			if (dialogState.spriteDialogItemId == null) throw new IllegalArgumentException("This handler is for sprite dialogs only.");

			ChargedWeapon chargedWeapon = ChargedWeapon.getChargedWeaponFromId(matchers.getSpriteDialogId());
			if (chargedWeapon != null)
			{
				manager.setCharges(chargedWeapon, chargedWeapon.getRechargeAmount());
			}
		};
	}

	public static MatchHandler genericInputChargeMessage()
	{
		return genericInputChargeMessage(1);
	}

	public static MatchHandler genericInputChargeMessage(int multiplier)
	{
		return (matchers, dialogState, optionSelected, manager) -> {
			if (manager.lastUsedOnWeapon == null) return;

			String chargeCountString = matchers.getNameMatcher().group(1).replaceAll(",", "");
			int maxChargeCount = Integer.parseInt(chargeCountString);
			int chargesEntered;
			try
			{
				chargesEntered = Integer.parseInt(optionSelected.replaceAll("k", "000").replaceAll("m", "000000").replaceAll("b", "000000000"));
			} catch (NumberFormatException e) {
				// can happen if the input is empty for example.
				return;
			}

			if (chargesEntered > maxChargeCount) chargesEntered = maxChargeCount;

			manager.addCharges(manager.lastUsedOnWeapon, chargesEntered * multiplier, true);
		};
	}

	public static MatchHandler genericUnchargeDialog()
	{
		return (matchers, dialogState, optionSelected, manager) -> {
			manager.setCharges(manager.lastUnchargeClickedWeapon, 0, true);
		};
	}
}
