package au.com.codeka.warworlds.common.sim;

import static au.com.codeka.warworlds.common.testing.Matchers.closeTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import au.com.codeka.warworlds.common.proto.BuildRequest;
import au.com.codeka.warworlds.common.proto.Colony;
import au.com.codeka.warworlds.common.proto.ColonyFocus;
import au.com.codeka.warworlds.common.proto.Design;
import au.com.codeka.warworlds.common.proto.EmpireStorage;
import au.com.codeka.warworlds.common.proto.Planet;
import au.com.codeka.warworlds.common.proto.Star;
import com.google.common.collect.Lists;
import javax.print.DocFlavor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link Simulation}.
 */
@RunWith(JUnit4.class)
public class SimulationTest {
  private final Simulation.LogHandler logHandler = new Simulation.LogHandler() {
    private String starName;

    @Override
    public void setStarName(String starName) {
      this.starName = starName;
    }

    @Override
    public void log(String message) {
      System.out.println(String.format("%s : %s", starName, message));
    }
  };

  /** The time to use as "now", so we're not trying to use the 'real' current time. */
  private static final long NOW_TIME = Simulation.STEP_TIME * 100L;

  /** Tests that a simulation on an idle star does nothing. */
  @Test
  public void testDoNothing() {
    Simulation sim = new Simulation(NOW_TIME, logHandler);

    Star.Builder starBuilder = new Star.Builder()
        .id(1L)
        .name("First");
    sim.simulate(starBuilder);

    assertThat(starBuilder.name, is("First"));
    assertThat(starBuilder.id, is(1L));
    assertThat(starBuilder.last_simulation, is(NOW_TIME));
  }

  /** Tests that we correctly simulate based on the simulation's STEP_TIME. */
  @Test
  public void testSimulateStepTime() {
    Simulation sim = new Simulation(NOW_TIME, false, logHandler);

    Star.Builder starBuilder = new Star.Builder()
        .id(1L)
        .name("Stardust");
    sim.simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME));

    sim = new Simulation(NOW_TIME + Simulation.STEP_TIME - 1, false, logHandler);
    sim.simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME));

    sim = new Simulation(NOW_TIME + Simulation.STEP_TIME, false, logHandler);
    sim.simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME + Simulation.STEP_TIME));
  }

  @Test
  public void testSimulateSimpleColony() {
    Star.Builder starBuilder = new Star.Builder()
        .id(1L)
        .planets(Lists.newArrayList(
            new Planet.Builder()
                .index(0)
                .energy_congeniality(100)
                .farming_congeniality(100)
                .mining_congeniality(100)
                .population_congeniality(1000)
                .planet_type(Planet.PLANET_TYPE.TERRAN)
                .colony(
                    new Colony.Builder()
                        .empire_id(1L)
                        .focus(
                            new ColonyFocus.Builder()
                                .energy(0.25f)
                                .farming(0.25f)
                                .mining(0.25f)
                                .construction(0.25f)
                                .build())
                        .population(100f)
                        .build())
                .build()
        ))
        .empire_stores(Lists.newArrayList(
            new EmpireStorage.Builder()
                .empire_id(1L)
                .max_energy(1000f)
                .max_goods(1000f)
                .max_minerals(1000f)
                .total_energy(100f)
                .total_goods(100f)
                .total_minerals(100f)
                .build()
        ))
        .name("Stardust");

    new Simulation(NOW_TIME, false, logHandler).simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME));
    assertThat(starBuilder.planets.get(0).colony.population, closeTo(101.67f, 2));
    assertThat(starBuilder.empire_stores.get(0).empire_id, is(1L));
    // 100 + 4.17 (=100 * focus=0.25 * time_step=0.166) - population_growth=1.67
    assertThat(starBuilder.empire_stores.get(0).total_goods, closeTo(102.50f, 2));
    assertThat(starBuilder.empire_stores.get(0).total_minerals, closeTo(104.17f, 2));
    assertThat(starBuilder.empire_stores.get(0).total_energy, closeTo(104.17f, 2));
  }

  @Test
  public void testSimulateColonyUnevenFocus() {
    Star.Builder starBuilder = new Star.Builder()
        .id(1L)
        .planets(Lists.newArrayList(
            new Planet.Builder()
                .index(0)
                .energy_congeniality(100)
                .farming_congeniality(100)
                .mining_congeniality(100)
                .population_congeniality(1000)
                .planet_type(Planet.PLANET_TYPE.TERRAN)
                .colony(
                    new Colony.Builder()
                        .empire_id(1L)
                        .focus(
                            new ColonyFocus.Builder()
                                .energy(0.1f)
                                .farming(0.2f)
                                .mining(0.3f)
                                .construction(0.4f)
                                .build())
                        .population(100f)
                        .build())
                .build()
        ))
        .empire_stores(Lists.newArrayList(
            new EmpireStorage.Builder()
                .empire_id(1L)
                .max_energy(1000f)
                .max_goods(1000f)
                .max_minerals(1000f)
                .total_energy(100f)
                .total_goods(100f)
                .total_minerals(100f)
                .build()
        ))
        .name("Stardust");

    new Simulation(NOW_TIME, false, logHandler).simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME));
    assertThat(starBuilder.planets.get(0).colony.population, closeTo(101.67f, 2));
    assertThat(starBuilder.empire_stores.get(0).empire_id, is(1L));
    // 100 + 4.17 (=100 * focus=0.2 * time_step=0.166) - population_growth=1.67
    assertThat(starBuilder.empire_stores.get(0).total_goods, closeTo(101.67f, 2));
    assertThat(starBuilder.empire_stores.get(0).total_minerals, closeTo(105.00f, 2));
    assertThat(starBuilder.empire_stores.get(0).total_energy, closeTo(101.67f, 2));
  }

  @Test
  public void testSimulateColonyUnevenCongeniality() {
    Star.Builder starBuilder = new Star.Builder()
        .id(1L)
        .planets(Lists.newArrayList(
            new Planet.Builder()
                .index(0)
                .energy_congeniality(100)
                .farming_congeniality(200)
                .mining_congeniality(300)
                .population_congeniality(1000)
                .planet_type(Planet.PLANET_TYPE.TERRAN)
                .colony(
                    new Colony.Builder()
                        .empire_id(1L)
                        .focus(
                            new ColonyFocus.Builder()
                                .energy(0.25f)
                                .farming(0.25f)
                                .mining(0.25f)
                                .construction(0.25f)
                                .build())
                        .population(100f)
                        .build())
                .build()
        ))
        .empire_stores(Lists.newArrayList(
            new EmpireStorage.Builder()
                .empire_id(1L)
                .max_energy(1000f)
                .max_goods(1000f)
                .max_minerals(1000f)
                .total_energy(100f)
                .total_goods(100f)
                .total_minerals(100f)
                .build()
        ))
        .name("Stardust");

    new Simulation(NOW_TIME, false, logHandler).simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME));
    assertThat(starBuilder.planets.get(0).colony.population, closeTo(101.67f, 2));
    assertThat(starBuilder.empire_stores.get(0).empire_id, is(1L));
    // 100 + 4.17 (=100 * focus=0.25 * congeniality=2.0 * time_step=0.166) - population_growth=1.67
    assertThat(starBuilder.empire_stores.get(0).total_goods, closeTo(106.67f, 2));
    assertThat(starBuilder.empire_stores.get(0).total_minerals, closeTo(112.50f, 2));
    assertThat(starBuilder.empire_stores.get(0).total_energy, closeTo(104.17f, 2));
  }

  @Test
  public void testBuildColonyShipEnoughEverything() {
    Star.Builder starBuilder = new Star.Builder()
        .id(1L)
        .planets(Lists.newArrayList(
            new Planet.Builder()
                .index(0)
                .energy_congeniality(100)
                .farming_congeniality(200)
                .mining_congeniality(300)
                .population_congeniality(1200)
                .planet_type(Planet.PLANET_TYPE.TERRAN)
                .colony(
                    new Colony.Builder()
                        .empire_id(1L)
                        .focus(
                            new ColonyFocus.Builder()
                                .energy(0.0f)
                                .farming(0.0f)
                                .mining(0.0f)
                                .construction(1.0f)
                                .build())
                        .population(1200f)
                        .build_requests(Lists.newArrayList(
                            new BuildRequest.Builder()
                                .count(1)
                                .design_type(Design.DesignType.COLONY_SHIP)
                                .id(1L)
                                .progress(0.0f)
                                .start_time(NOW_TIME)
                                .build()
                        ))
                        .build())
                .build()
        ))
        .empire_stores(Lists.newArrayList(
            new EmpireStorage.Builder()
                .empire_id(1L)
                .max_energy(1000f)
                .max_goods(1000f)
                .max_minerals(1000f)
                .total_energy(100f)
                .total_goods(100f)
                .total_minerals(120f)
                .build()
        ))
        .name("Stardust");

    new Simulation(NOW_TIME, false, logHandler).simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME));
    // We're 120% over quota so we should finish in 1/1.2 of a time step.
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (long)((1 / 1.2) * Simulation.STEP_TIME)));

    starBuilder = new Star.Builder()
        .id(1L)
        .planets(Lists.newArrayList(
            new Planet.Builder()
                .index(0)
                .energy_congeniality(100)
                .farming_congeniality(200)
                .mining_congeniality(300)
                .population_congeniality(1200)
                .planet_type(Planet.PLANET_TYPE.TERRAN)
                .colony(
                    new Colony.Builder()
                        .empire_id(1L)
                        .focus(
                            new ColonyFocus.Builder()
                                .energy(0.0f)
                                .farming(0.0f)
                                .mining(0.0f)
                                .construction(1.0f)
                                .build())
                        .population(1000f)
                        .build_requests(Lists.newArrayList(
                            new BuildRequest.Builder()
                                .count(1)
                                .design_type(Design.DesignType.COLONY_SHIP)
                                .id(1L)
                                .progress(0.0f)
                                .start_time(NOW_TIME)
                                .build()
                        ))
                        .build())
                .build()
        ))
        .empire_stores(Lists.newArrayList(
            new EmpireStorage.Builder()
                .empire_id(1L)
                .max_energy(1000f)
                .max_goods(1000f)
                .max_minerals(1000f)
                .total_energy(100f)
                .total_goods(100f)
                .total_minerals(120f)
                .build()
        ))
        .name("Stardust");

    new Simulation(NOW_TIME, false, logHandler).simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME));
    // We're 100% over quota on population so we should finish exactly at the end of the step.
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + Simulation.STEP_TIME));

    starBuilder = new Star.Builder()
        .id(1L)
        .planets(Lists.newArrayList(
            new Planet.Builder()
                .index(0)
                .energy_congeniality(100)
                .farming_congeniality(200)
                .mining_congeniality(300)
                .population_congeniality(1200)
                .planet_type(Planet.PLANET_TYPE.TERRAN)
                .colony(
                    new Colony.Builder()
                        .empire_id(1L)
                        .focus(
                            new ColonyFocus.Builder()
                                .energy(0.0f)
                                .farming(0.0f)
                                .mining(0.0f)
                                .construction(1.0f)
                                .build())
                        .population(1200f)
                        .build_requests(Lists.newArrayList(
                            new BuildRequest.Builder()
                                .count(1)
                                .design_type(Design.DesignType.COLONY_SHIP)
                                .id(1L)
                                .progress(0.0f)
                                .start_time(NOW_TIME)
                                .build()
                        ))
                        .build())
                .build()
        ))
        .empire_stores(Lists.newArrayList(
            new EmpireStorage.Builder()
                .empire_id(1L)
                .max_energy(1000f)
                .max_goods(1000f)
                .max_minerals(1000f)
                .total_energy(100f)
                .total_goods(100f)
                .total_minerals(100f)
                .build()
        ))
        .name("Stardust");

    new Simulation(NOW_TIME, false, logHandler).simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME));
    // We're 100% over quota on minerals so we should finish exactly at the end of the step.
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + Simulation.STEP_TIME));
  }


  @Test
  public void testBuildColonyShipInsufficientPopulation() {
    Star.Builder starBuilder = new Star.Builder()
        .id(1L)
        .planets(Lists.newArrayList(
            new Planet.Builder()
                .index(0)
                .energy_congeniality(100)
                .farming_congeniality(200)
                .mining_congeniality(300)
                .population_congeniality(1000)
                .planet_type(Planet.PLANET_TYPE.TERRAN)
                .colony(
                    new Colony.Builder()
                        .empire_id(1L)
                        .focus(
                            new ColonyFocus.Builder()
                                .energy(0.0f)
                                .farming(0.9f)
                                .mining(0.0f)
                                .construction(0.1f)
                                .build())
                        .population(1000f)
                        .build_requests(Lists.newArrayList(
                            new BuildRequest.Builder()
                                .count(1)
                                .design_type(Design.DesignType.COLONY_SHIP)
                                .id(1L)
                                .progress(0.0f)
                                .start_time(NOW_TIME)
                                .build()
                        ))
                        .build())
                .build()
        ))
        .empire_stores(Lists.newArrayList(
            new EmpireStorage.Builder()
                .empire_id(1L)
                .max_energy(1000f)
                .max_goods(1000f)
                .max_minerals(1000f)
                .total_energy(100f)
                .total_goods(100f)
                .total_minerals(120f)
                .build()
        ))
        .name("Stardust");

    new Simulation(NOW_TIME, false, logHandler).simulate(starBuilder);
    assertThat(starBuilder.last_simulation, is(NOW_TIME));
    // We only have 10% of the required population, so it should take 10 steps to complete.
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.92f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.08f, 2));

    // And it should continue taking 10 steps as we continue simulating.
    new Simulation(NOW_TIME + Simulation.STEP_TIME, false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.92f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.08f, 2));

    // Note as we progress, the efficiency of the population improves as we need less and less
    // population relative to when we started.
    new Simulation(NOW_TIME + (2 * Simulation.STEP_TIME), false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.90f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.10f, 2));

    new Simulation(NOW_TIME + (3 * Simulation.STEP_TIME), false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.90f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.10f, 2));

    new Simulation(NOW_TIME + (4 * Simulation.STEP_TIME), false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.86f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.14f, 2));

    new Simulation(NOW_TIME + (5 * Simulation.STEP_TIME), false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.86f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.14f, 2));

    new Simulation(NOW_TIME + (6 * Simulation.STEP_TIME), false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.80f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.20f, 2));

    new Simulation(NOW_TIME + (7 * Simulation.STEP_TIME), false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.80f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.20f, 2));

    new Simulation(NOW_TIME + (8 * Simulation.STEP_TIME), false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.67f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.33f, 2));

    new Simulation(NOW_TIME + (9 * Simulation.STEP_TIME), false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.67f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.33f, 2));

    new Simulation(NOW_TIME + (10 * Simulation.STEP_TIME), false, logHandler).simulate(starBuilder);
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).end_time,
        is(NOW_TIME + (10 * Simulation.STEP_TIME)));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).progress, closeTo(1.0f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).minerals_efficiency,
        closeTo(0.67f, 2));
    assertThat(
        starBuilder.planets.get(0).colony.build_requests.get(0).population_efficiency,
        closeTo(0.33f, 2));
  }

}