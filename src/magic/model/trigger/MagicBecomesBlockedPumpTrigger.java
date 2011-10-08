package magic.model.trigger;

import magic.model.MagicGame;
import magic.model.MagicPermanent;
import magic.model.action.MagicChangeTurnPTAction;
import magic.model.event.MagicEvent;

public class MagicBecomesBlockedPumpTrigger extends MagicWhenBecomesBlockedTrigger {

	private final int amountPower;
	private final int amountToughness;
	
    public MagicBecomesBlockedPumpTrigger(final int amountPower,final int amountToughness) {
    	this.amountPower = amountPower;
    	this.amountToughness = amountToughness;
	}

    @Override
	public MagicEvent executeTrigger(final MagicGame game,final MagicPermanent permanent,final MagicPermanent data) {
		return (permanent == data ) ?
            new MagicEvent(
                    permanent,
                    permanent.getController(),
                    new Object[]{permanent},
                    this,
                    permanent + " gets +" + amountPower + "/+" +  amountToughness + " until end of turn."):
            MagicEvent.NONE;
	}
	@Override
	public void executeEvent(
            final MagicGame game,
            final MagicEvent event,
            final Object data[],
            final Object[] choiceResults) {
		game.doAction(new MagicChangeTurnPTAction(
				(MagicPermanent)data[0],
				amountPower,
				amountToughness));
	}
}

