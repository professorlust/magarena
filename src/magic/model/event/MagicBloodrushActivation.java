package magic.model.event;

import magic.model.MagicCard;
import magic.model.MagicCardDefinition;
import magic.model.MagicGame;
import magic.model.MagicLocationType;
import magic.model.MagicManaCost;
import magic.model.MagicPayedCost;
import magic.model.MagicSource;
import magic.model.action.MagicDrawAction;
import magic.model.action.MagicPutItemOnStackAction;
import magic.model.choice.MagicTargetChoice;
import magic.model.condition.MagicCondition;
import magic.model.stack.MagicCardOnStack;
import magic.model.stack.MagicAbilityOnStack;

public abstract class MagicBloodrushActivation extends MagicCardActivation {

    final MagicManaCost cost;

    public MagicBloodrushActivation(final MagicManaCost aCost) {
        super(
            new MagicCondition[]{
                aCost.getCondition()
            },
            new MagicActivationHints(MagicTiming.Pump,true),
            "Bloodrush"
        );
        cost = aCost;
    }
  
    public MagicEvent[] getCostEvent(final MagicCard source) {
        return new MagicEvent[]{
            new MagicPayManaCostEvent(
                source,
                source.getController(),
                cost
            ),
            new MagicDiscardCardEvent(
                source,
                source.getController(),
                source
            )
        };
    }

    @Override
    public MagicEvent getEvent(final MagicSource source) {
        return new MagicEvent(
            source,
            new MagicEventAction() {
                @Override
                public void executeEvent(final MagicGame game,final MagicEvent event,final Object[] choices) {
                    final MagicAbilityOnStack abilityOnStack = new MagicAbilityOnStack(
                        MagicBloodrushActivation.this,
                        getCardEvent(event.getCard(), game.getPayedCost()), 
                        game.getPayedCost()
                    );
                    game.doAction(new MagicPutItemOnStackAction(abilityOnStack));
                }
            },
            "Bloodrush."
        );
    }
    
    public abstract MagicEvent getCardEvent(final MagicCard source,final MagicPayedCost payedCost);

    @Override
    final MagicTargetChoice getTargetChoice(final MagicCard source) {
        return getCardEvent(source,MagicPayedCost.NO_COST).getTargetChoice();
    }
}
