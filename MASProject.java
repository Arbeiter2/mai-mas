/*
 * Copyright (C) 2011-2016 Rinde van Lon, iMinds-DistriNet, KU Leuven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static com.google.common.collect.Lists.newArrayList;

import java.util.ArrayList;
import java.util.Iterator;
import javax.measure.unit.SI;

import com.github.rinde.rinsim.core.Simulator;
import com.github.rinde.rinsim.core.model.comm.CommModel;
import com.github.rinde.rinsim.core.model.road.CollisionGraphRoadModel;
import com.github.rinde.rinsim.core.model.road.RoadModelBuilders;
import com.github.rinde.rinsim.geom.Graph;
import com.github.rinde.rinsim.geom.Graphs;
import com.github.rinde.rinsim.geom.LengthData;
import com.github.rinde.rinsim.geom.ListenableGraph;
import com.github.rinde.rinsim.geom.Point;
import com.github.rinde.rinsim.geom.TableGraph;
import com.github.rinde.rinsim.ui.View;
import com.github.rinde.rinsim.ui.renderers.AGVRenderer;
import com.github.rinde.rinsim.ui.renderers.CommRenderer;
import com.github.rinde.rinsim.ui.renderers.WarehouseRenderer;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;

/**
 * Example showcasing the {@link CollisionGraphRoadModel} with an
 * {@link WarehouseRenderer} and {@link AGVRenderer}.
 * @author Rinde van Lon
 */
public final class MASProject {

  private static final double VEHICLE_LENGTH = 2d;
  private static final int NUM_AGVS = 12;
  private static final int NUM_AGENTS = 14;
  private static final long TEST_END_TIME = 10 * 60 * 1000L;
  private static final int TEST_SPEED_UP = 16;
  private static final ArrayList<Point> transportAgentLocations = new ArrayList<Point>();
  private static final ArrayList<Point> transportAgentExtents = new ArrayList<Point>();

  private static final ArrayList<Point> chargeStationLocations = new ArrayList<Point>();
  private static final ArrayList<Point> chargeStationExtents = new ArrayList<Point>();
  
  private MASProject() {}

  /**
   * @param args - No args.
   */
  public static void main(String[] args) {
    run(false);
  }

  /**
   * Runs the example.
   * @param testing If <code>true</code> the example will run in testing mode,
   *          automatically starting and stopping itself such that it can be run
   *          from a unit test.
   */
  public static void run(boolean testing) {
    View.Builder viewBuilder = View.builder()
      .with(WarehouseRenderer.builder()
        .withMargin(VEHICLE_LENGTH))
//      .with(CommRenderer.builder()
//    	.withReliabilityColors()
//    	.withMessageCount())
      .with(AGVRenderer.builder()
        .withDifferentColorsForVehicles()
        .withVehicleOrigin()
        .withVehicleCreationNumber());

    if (testing) {
      viewBuilder = viewBuilder.withAutoPlay()
        .withAutoClose()
        .withSimulatorEndTime(TEST_END_TIME)
        .withTitleAppendix("TESTING")
        .withSpeedUp(TEST_SPEED_UP);
    } else {
      viewBuilder = viewBuilder.withTitleAppendix("DynCNET");
    }

    final Simulator sim = Simulator.builder()
      .addModel(
        RoadModelBuilders.dynamicGraph(GraphCreator.createGraph())
          .withCollisionAvoidance()
          .withDistanceUnit(SI.METER)
          .withVehicleLength(VEHICLE_LENGTH)
      	  .withMinDistance(1d))
      .addModel(viewBuilder)
      .addModel(CommModel.builder())
      .build();

    Iterator<Point> iter1 = chargeStationLocations.iterator();
    for (int i = 0; i < NUM_AGVS; i++) {
        Point loc = iter1.next();
    	sim.register(new AGV(sim.getRandomGenerator(), loc));
    	sim.register(new ChargeStation(sim.getRandomGenerator(), loc, 
    			chargeStationExtents.get(i)));
    }
    
    Iterator<Point> iter = transportAgentLocations.iterator();
    for (int i=0; i < Math.min(NUM_AGENTS, transportAgentLocations.size()); i++)
    {
    	PDPStation a = new PDPStation(sim.getRandomGenerator(), iter.next(), 
    			transportAgentExtents.get(i));
    	sim.register(a);
    	System.out.println(a.toString());
    }

    sim.start();
  }

  static class GraphCreator {
    static final int LEFT_CENTER_U_ROW = 4;
    static final int LEFT_CENTER_L_ROW = 5;
    static final int LEFT_COL = 4;
    static final int RIGHT_CENTER_U_ROW = 2;
    static final int RIGHT_CENTER_L_ROW = 4;
    static final int RIGHT_COL = 0;

    GraphCreator() {}

    static ImmutableTable<Integer, Integer, Point> createMatrix(int cols,
        int rows, Point offset) {
      final ImmutableTable.Builder<Integer, Integer, Point> builder =
        ImmutableTable.builder();
      for (int c = 0; c < cols; c++) {
        for (int r = 0; r < rows; r++) {
          builder.put(r, c, new Point(
            offset.x + c * VEHICLE_LENGTH * 4,
            offset.y + r * VEHICLE_LENGTH * 2));
        }
      }
      return builder.build();
    }

    static ListenableGraph<LengthData> createSimpleGraph() {
      final Graph<LengthData> g = new TableGraph<>();

      final Table<Integer, Integer, Point> matrix = createMatrix(8, 6,
        new Point(0, 0));

      for (int i = 0; i < matrix.columnMap().size(); i++) {

        Iterable<Point> path;
        if (i % 2 == 1) {
          path = Lists.reverse(newArrayList(matrix.column(i).values()));
        } else {
          path = matrix.column(i).values();
        }
        Graphs.addPath(g, path);
      }

      Graphs.addPath(g, matrix.row(0).values());
      Graphs.addPath(g, Lists.reverse(newArrayList(matrix.row(
        matrix.rowKeySet().size() - 1).values())));

      return new ListenableGraph<>(g);
    }
    
    /**
     * add bay to warehouse graph, and record agent location
     * 
     * @param g - graph
     * @param x1 - x-position of link start
     * @param y1 - y-position of link start
     * @param x2 - x-position of link end
     * @param y2 - y-position of link end
     * @param useFirst - if true, use x1,y1 as agent location; otherwise use x2,y2
     */
	static void addTransportAgentLocation(Graph<LengthData> g,
    		double x1, double y1, double x2, double y2, boolean useFirst)
    {
        ArrayList<Point> q = new ArrayList<Point>();
        Point p1 = new Point(x1, y1);
        q.add(p1);
        Point p2 = new Point(x2, y2);
        q.add(p2);
        Graphs.addBiPath(g, q);
        
        if (useFirst)
        	transportAgentLocations.add(p1);
        else
        	transportAgentLocations.add(p2);
        
        if (useFirst)
        	MASProject.transportAgentExtents.add(new Point(x2-x1,0));
        else
        	MASProject.transportAgentExtents.add(new Point(x1-x2,0));
    }


	static void addChargeStationLocation(Graph<LengthData> g,
    		double x1, double y1, double x2, double y2, boolean useFirst)
    {
        ArrayList<Point> q = new ArrayList<Point>();
        Point p1 = new Point(x1, y1);
        q.add(p1);
        Point p2 = new Point(x2, y2);
        q.add(p2);
        Graphs.addBiPath(g, q);
        
        
        if (useFirst)
        	chargeStationLocations.add(p1);
        else
        	chargeStationLocations.add(p2);
        
        if (useFirst)
        	MASProject.chargeStationExtents.add(new Point(x2-x1,0));
        else
        	MASProject.chargeStationExtents.add(new Point(x1-x2,0));
        
    }
	
    static ListenableGraph<LengthData> createGraph() {
      final Graph<LengthData> g = new TableGraph<>();

      final Table<Integer, Integer, Point> matrix = createMatrix(8, 10,
        new Point(0, 0));

      for (int i = 0; i < matrix.columnMap().size(); i++) 
      {
          Iterable<Point> path;
          if (i % 2 == 0) {
        	  path = Lists.reverse(newArrayList(matrix.column(i).values()));
          }
          else
          {
              path = matrix.column(i).values();
          }
          Graphs.addPath(g, path);
      }

      for (int i=0; i < 7; i++)
      {
    	  addChargeStationLocation(g, i * 8.0, 4.0, i * 8.0 + 3d, 4.0, false);
    	  addChargeStationLocation(g, i * 8.0 + 5d, 32.0, (i + 1) * 8d, 32.0, true);
      
    	  addTransportAgentLocation(g, i * 8.0, 12.0, i * 8.0 + 3d, 12.0, false);
    	  addTransportAgentLocation(g, i * 8.0 + 5d, 24.0, (i + 1) * 8d, 24.0, true);
      
      }
      /*          
     
      // block -1
      addTransportAgentLocation(g, 0.0, 4.0, 3.0, 4.0, false);
      addTransportAgentLocation(g, 5.0, 32.0, 8.0, 32.0, true); 

      addTransportAgentLocation(g, 8.0, 4.0, 11.0, 4.0, false); 
      addTransportAgentLocation(g, 13.0, 32.0, 16.0, 32.0, true); 
      
      addTransportAgentLocation(g, 16.0, 4.0, 19.0, 4.0, false);
      addTransportAgentLocation(g, 21.0, 32.0, 24.0, 32.0, true);
      
      addTransportAgentLocation(g, 24.0, 4.0, 27.0, 4.0, false);
      addTransportAgentLocation(g, 32.0, 4.0, 35.0, 4.0, false);
      
      addTransportAgentLocation(g, 45.0, 4.0, 48.0, 4.0, true);
      addTransportAgentLocation(g, 29.0, 32.0, 32.0, 32.0, true);
      
      addTransportAgentLocation(g, 37.0, 32.0, 40.0, 32.0, true);
      addTransportAgentLocation(g, 53.0, 32.0, 56.0, 32.0, true);
      
      

   
      // block 0
      addTransportAgentLocation(g, 0.0, 8.0, 3.0, 8.0, false);
      addTransportAgentLocation(g, 0.0, 24.0, 3.0, 24.0, false);
      // block 1
      addTransportAgentLocation(g, 8.0, 12.0, 11.0, 12.0, false);
      addTransportAgentLocation(g, 13.0, 24.0, 16.0, 24.0, true);
      // block 2
      addTransportAgentLocation(g, 21.0, 8.0, 24.0, 8.0, true);
      addTransportAgentLocation(g, 21.0, 28.0, 24.0, 28.0, true);
      // block 3
      addTransportAgentLocation(g, 29.0, 12.0, 32.0, 12.0, true);
      addTransportAgentLocation(g, 29.0, 24.0, 32.0, 24.0, true);
     // block 4
      addTransportAgentLocation(g, 32.0, 12.0, 35.0, 12.0, false);
      addTransportAgentLocation(g, 32.0, 24.0, 35.0, 24.0, false);
      // block 5
      addTransportAgentLocation(g, 40.0, 28.0, 43.0, 28.0, false);
      addTransportAgentLocation(g, 45.0, 16.0, 48.0, 16.0, true);
      // block 7
      addTransportAgentLocation(g, 48.0, 8.0, 51.0, 8.0, false);
      addTransportAgentLocation(g, 53.0, 24.0, 56.0, 24.0, true);
*/ 
      
      Graphs.addPath(g, matrix.row(0).values());
      Graphs.addPath(g, Lists.reverse(newArrayList(matrix.row(
        matrix.rowKeySet().size() - 1).values())));
      
      System.out.println(g.toString());

      return new ListenableGraph<>(g);
    }
  }
}
