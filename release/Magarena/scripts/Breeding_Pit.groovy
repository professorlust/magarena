[
	new MagicAtUpkeepTrigger() {
    	@Override
        public MagicEvent executeTrigger(final MagicGame game,final MagicPermanent permanent,final MagicPlayer upkeepPlayer) {
        	return permanent.isController(upkeepPlayer) ? new MagicEvent( permanent, new MagicMayChoice( new MagicPayManaCostChoice(MagicManaCost.create("{B}{B}"))), this,"PN may pay {B}{B}. If you don't, sacrifice SN.") : MagicEvent.NONE;
        }
        
        @Override
        public void executeEvent(
                final MagicGame game,
                final MagicEvent event,
                final Object[] choiceResults) {
        	if (MagicMayChoice.isNoChoice(choiceResults[0])) {
                game.doAction(new MagicSacrificeAction(event.getPermanent()));
            }
        }
    },
    new MagicAtEndOfTurnTrigger() {
    	@Override
    	MagicEvent executeTrigger(final MagicGame game,final MagicPermanent permanent,final MagicPlayer eotPlayer) {
    		return permanent.isController(eotPlayer) ? new MagicEvent(permanent, this, "PN puts a 0/1 black Thrull creature token into play"):MagicEvent.NONE;
    	
    	}
    	
    	@Override
        public void executeEvent(
        		final MagicGame game,
            	final MagicEvent event,
            	final Object[] choiceResults) {
        	game.doAction(new MagicPlayTokenAction(event.getPlayer(),TokenCardDefinitions.get("Thrull1")));
        }
    }
]