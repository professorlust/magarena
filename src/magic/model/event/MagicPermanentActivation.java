package magic.model.event;

import magic.model.MagicCardDefinition;
import magic.model.MagicManaCost;
import magic.model.MagicChangeCardDefinition;
import magic.model.MagicGame;
import magic.model.MagicPayedCost;
import magic.model.MagicPermanent;
import magic.model.MagicPlayer;
import magic.model.MagicSource;
import magic.model.MagicCopyable;
import magic.model.MagicCopyMap;
import magic.model.MagicCounterType;
import magic.model.MagicLocationType;
import magic.model.MagicPowerToughness;
import magic.model.action.MagicTargetAction;
import magic.model.action.MagicAddStaticAction;
import magic.model.action.MagicPutItemOnStackAction;
import magic.model.action.MagicChangeCountersAction;
import magic.model.action.MagicUntapAction;
import magic.model.action.MagicPreventDamageAction;
import magic.model.action.MagicRemoveFromPlayAction;
import magic.model.choice.MagicTargetChoice;
import magic.model.choice.MagicChoice;
import magic.model.condition.MagicCondition;
import magic.model.stack.MagicAbilityOnStack;
import magic.model.target.MagicPreventTargetPicker;
import magic.model.target.MagicTargetPicker;
import magic.model.target.MagicTarget;
import magic.model.mstatic.MagicStatic;
import magic.model.mstatic.MagicLayer;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;

public abstract class MagicPermanentActivation extends MagicActivation<MagicPermanent> implements MagicChangeCardDefinition, MagicCopyable {

    public MagicPermanentActivation(final MagicActivationHints hints, final String txt) {
        super(MagicActivation.NO_COND,hints,txt);
    }

    public MagicPermanentActivation(final MagicCondition[] conditions, final MagicActivationHints hints, final String txt) {
        super(conditions,hints,txt);
    }

    @Override
    public final boolean usesStack() {
        return true;
    }

    @Override
    public final MagicEvent getEvent(final MagicSource source) {
        return new MagicEvent(
            source,
            this,
            EVENT_ACTION,
            "Play activated ability of SN."
        );
    }
    
    @Override
    public final boolean canPlay(final MagicGame game, final MagicPlayer player, final MagicPermanent source, final boolean useHints) {
        final boolean superCanPlay = super.canPlay(game, player, source, useHints);
       
        // More complex check that first executes events without choice, then check conditions of the others
        if (superCanPlay && source.producesMana()) {
            game.record();
            for (final MagicEvent event : getCostEvent(source)) {
                if (event.hasChoice() == false) {
                    game.executeEvent(event, MagicEvent.NO_CHOICE_RESULTS);
                }
            }
            for (final MagicEvent event : getCostEvent(source)) {
                if (event.hasChoice() == true) {
                    for (final MagicCondition condition : event.getConditions()) {
                        if (!condition.accept(source)) {
                            game.restore();
                            return false;
                        }
                    }
                }
            }
            game.restore();
        }

        return superCanPlay;
    }

    @Override
    public MagicCopyable copy(final MagicCopyMap copyMap) {
        return this;
    }

    private static final MagicEventAction EVENT_ACTION=new MagicEventAction() {
        @Override
        public final void executeEvent(final MagicGame game, final MagicEvent event) {
            final MagicPermanentActivation permanentActivation = event.getRefPermanentActivation();
            final MagicPermanent permanent = event.getPermanent();
            final MagicAbilityOnStack abilityOnStack = new MagicAbilityOnStack(
                permanentActivation,
                permanent,
                game.getPayedCost()
            );
            game.doAction(new MagicPutItemOnStackAction(abilityOnStack));
        }
    };

    @Override
    public final MagicChoice getChoice(final MagicPermanent source) {
        return getPermanentEvent(source,MagicPayedCost.NO_COST).getChoice();
    }

    public abstract Iterable<? extends MagicEvent> getCostEvent(final MagicPermanent source);

    public abstract MagicEvent getPermanentEvent(final MagicPermanent source,final MagicPayedCost payedCost);

    @Override
    public void executeEvent(final MagicGame game, final MagicEvent event) {
        throw new RuntimeException(getClass() + " did not override executeEvent");
    }

    @Override
    public void change(final MagicCardDefinition cdef) {
        cdef.addAct(this);
    }
    
    private static final String COLON = "\\s*:\\s*";
    private static final String COMMA = "\\s*,\\s*";

    public static final MagicPermanentActivation create(final String act) {
        final String[] token = act.split(COLON, 2);
        final String cost = token[0];
        final String rule = token[1];
        
        final String[] costs = cost.split(COMMA);
        
        final MagicSourceEvent sourceEvent = MagicRuleEventAction.create(rule);
        final MagicRuleEventAction ruleAction = sourceEvent.getRule();

        assert englishToCostEvents(costs, MagicPermanent.NONE).size() > 0;

        final boolean isCostIndependent = (
               cost.contains("{T}") 
            || cost.contains("Sacrifice SN") 
            || cost.contains("{E}") 
            || cost.contains("{Q}") 
            || cost.contains("{Once}")
            || cost.contains("Return SN to its owner's hand")
            || cost.contains(" counters from SN")
        ) == false;

        return new MagicPermanentActivation(
            ruleAction.getConditions(rule),
            new MagicActivationHints(
                ruleAction.getTiming(rule),
                isCostIndependent && ruleAction.isIndependent()
            ),
            ruleAction.getName(rule)
        ) {
            @Override
            public Iterable<? extends MagicEvent> getCostEvent(final MagicPermanent source) {
                return englishToCostEvents(costs, source);
            }
       
            @Override
            public MagicEvent getPermanentEvent(final MagicPermanent source, final MagicPayedCost payedCost) {
                return sourceEvent.getEvent(source);
            }
        };
    }
    
    private static final List<MagicEvent> englishToCostEvents(final String[] costs, final MagicPermanent source) {
        final List<MagicEvent> events = new LinkedList<MagicEvent>();
        for (String cost : costs) {
            if (cost.equals("Sacrifice SN")) {
                events.add(new MagicSacrificeEvent(source));
            } else if (cost.equals("Sacrifice an artifact")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_ARTIFACT));
            } else if (cost.equals("Sacrifice a creature")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_CREATURE));
            } else if (cost.equals("Sacrifice a Goblin")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_GOBLIN));
            } else if (cost.equals("Sacrifice a Saproling")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_SAPROLING));
            } else if (cost.equals("Sacrifice a Beast")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_BEAST));
            } else if (cost.equals("Sacrifice a land")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_LAND));
            } else if (cost.equals("Sacrifice an Elf")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_ELF));
            } else if (cost.equals("Sacrifice a Bat")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_BAT));
            } else if (cost.equals("Sacrifice a Samurai")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_SAMURAI));
            } else if (cost.equals("Sacrifice a Cleric")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_CLERIC));
            } else if (cost.equals("Sacrifice a Human")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_HUMAN));
            } else if (cost.equals("Sacrifice an Elemental")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_ELEMENTAL));
            } else if (cost.equals("Sacrifice a Wall")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_WALL));
            } else if (cost.equals("Sacrifice a permanent")) {
                events.add(new MagicSacrificePermanentEvent(source,MagicTargetChoice.SACRIFICE_PERMANENT));
            } else if (cost.equals("Discard a card")) {
                events.add(new MagicDiscardEvent(source));
            } else if (cost.equals("Discard two cards")) {
                events.add(new MagicDiscardEvent(source, 2));
            } else if (cost.equals("{E}")) {
                events.add(new MagicExileEvent(source));
            } else if (cost.equals("{T}")) {
                events.add(new MagicTapEvent(source));
            } else if (cost.equals("{Q}")) {
                events.add(new MagicUntapEvent(source));
            } else if (cost.equals("Pay 1 life")) {
                events.add(new MagicPayLifeEvent(source, 1));
            } else if (cost.equals("Pay 2 life")) {
                events.add(new MagicPayLifeEvent(source, 2));
            } else if (cost.equals("Pay 3 life")) {
                events.add(new MagicPayLifeEvent(source, 3));
            } else if (cost.equals("Pay 7 life")) {
                events.add(new MagicPayLifeEvent(source, 7));
            } else if (cost.equals("{Once}")) {
                events.add(new MagicPlayAbilityEvent(source));
            } else if (cost.equals("{Sorcery}")) {
                events.add(new MagicSorceryConditionEvent(source));
            } else if (cost.equals("Return SN to its owner's hand")) {
                events.add(new MagicBouncePermanentEvent(source,source));
            } else if (cost.contains("Remove ")) {
            	final String[] costText = cost.replace("Remove ","").replace("\\scounter\\s|\\scounters\\s","").replace("from SN","").split(" ");
            	final int amount = englishToInt(costText[0]);
            	final String counterType = costText[1];
                events.add(new MagicRemoveCounterEvent(source,MagicCounterType.getCounterRaw(counterType),amount));
            } else {
                events.add(new MagicPayManaCostEvent(source, MagicManaCost.create(cost)));
            }
        }
        return events;
    }

    public static final MagicPermanentActivation SwitchPT(final MagicManaCost cost) {
        return new MagicPermanentActivation(
            new MagicActivationHints(MagicTiming.Pump),
            "Switch"
        ) {
            @Override
            public Iterable<? extends MagicEvent> getCostEvent(final MagicPermanent source) {
                return Arrays.asList(new MagicPayManaCostEvent(source,cost));
            }
            @Override
            public MagicEvent getPermanentEvent(final MagicPermanent source,final MagicPayedCost payedCost) {
                return new MagicEvent(
                    source,
                    this,
                    "Switch SN's power and toughness until end of turn."
                );
            }
            @Override
            public void executeEvent(final MagicGame game, final MagicEvent event) {
                game.doAction(new MagicAddStaticAction(event.getPermanent(), MagicStatic.SwitchPT));
            }
        };
    }
    
    public static int englishToInt(String num) {
        switch (num) {
            case "a": return 1;
            case "an": return 1;
            case "two": return 2;
            case "three" : return 3;
            case "four" : return 4;
            case "five" : return 5;
            case "six" : return 6;
            case "seven" : return 7;
            case "eight" : return 8;
            case "nine" : return 9;
            case "ten" : return 10;
            default: throw new RuntimeException("Unknown count: " + num);
        }
    }
}
